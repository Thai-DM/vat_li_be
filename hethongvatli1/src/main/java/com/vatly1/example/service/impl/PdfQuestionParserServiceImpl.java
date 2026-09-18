package com.vatly1.example.service.impl;

import com.vatly1.example.dto.CreateQuestionDTO;
import com.vatly1.example.dto.CreateQuestionDTO.CreateQuestionOptionDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.service.IPdfQuestionParserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfQuestionParserServiceImpl implements IPdfQuestionParserService {

    private static final Pattern ANSWER_TABLE_PATTERN = Pattern.compile(
            "(?i)(?:BẢNG\\s*ĐÁP\\s*ÁN|BANG\\s*DAP\\s*AN|ĐÁP\\s*ÁN\\s*TRẮC\\s*NGHIỆM|DAP\\s*AN\\s*TRAC\\s*NGHIEM|HƯỚNG\\s*DẪN\\s*CHẤM|HUONG\\s*DAN\\s*CHAM|KEY\\s*ĐÁP\\s*ÁN|KEY\\s*DAP\\s*AN)[\\s\\S]*"
    );

    private static final Pattern ANSWER_KEY_ENTRY_PATTERN = Pattern.compile(
            "(?:(?:C[aâ]u|Question)\\s*)?(\\d+)[\\s.:\\-_\\)]+([A-D])\\b", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUESTION_BLOCK_PATTERN = Pattern.compile(
            "(?i)(?:^|\\n)\\s*(?:C[aâ]u|B[aà]i|Question|Q)\\s*(\\d+)\\s*[.:\\-\\)]([\\s\\S]*?)(?=(?:(?:^|\\n)\\s*(?:C[aâ]u|B[aà]i|Question|Q)\\s*\\d+\\s*[.:\\-\\)]|\\b(?:BẢNG\\s*ĐÁP\\s*ÁN|BANG\\s*DAP\\s*AN|ĐÁP\\s*ÁN|DAP\\s*AN|HƯỚNG\\s*DẪN|HUONG\\s*DAN)\\b|$))"
    );

    private static final Pattern INLINE_ANSWER_PATTERN = Pattern.compile(
            "(?i)(?:Đáp\\s*án|Dap\\s*an|Đ/A|D/A|Key|Đáp\\s*án\\s*đúng|Chọn|Chon)\\s*[:=]?\\s*([A-D])\\b"
    );

    @Override
    public ParsedResult parseQuestionsFromPdf(InputStream inputStream, UUID subjectId, UUID topicId) {
        String fullText;
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            fullText = stripper.getText(document);
        } catch (IOException e) {
            log.error("Error reading PDF file", e);
            throw new CustomException("Không thể đọc nội dung file PDF: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        if (fullText == null || fullText.trim().isEmpty()) {
            throw new CustomException("File PDF không có nội dung văn bản (có thể là file ảnh scan hoặc file rỗng)", HttpStatus.BAD_REQUEST);
        }

        // Normalize all newlines
        fullText = fullText.replace("\r\n", "\n").replace('\r', '\n');
        log.info("Extracted PDF text:\n{}", fullText);

        List<CreateQuestionDTO> parsedQuestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Check for answer table at the end of the PDF
        Map<Integer, String> answerKeyTable = parseAnswerKeyTable(fullText);

        // 2. Parse individual question blocks
        Matcher questionMatcher = QUESTION_BLOCK_PATTERN.matcher(fullText);
        int questionIndex = 0;

        while (questionMatcher.find()) {
            questionIndex++;
            int questionNum;
            try {
                questionNum = Integer.parseInt(questionMatcher.group(1).trim());
            } catch (NumberFormatException e) {
                questionNum = questionIndex;
            }

            String rawBlock = questionMatcher.group(2).trim();
            CreateQuestionDTO questionDTO = parseQuestionBlock(rawBlock, questionNum, subjectId, topicId, answerKeyTable, warnings);
            if (questionDTO != null) {
                parsedQuestions.add(questionDTO);
            }
        }

        if (parsedQuestions.isEmpty()) {
            warnings.add("Không tìm thấy câu hỏi nào có định dạng chuẩn (ví dụ: 'Câu 1: ... A. ... B. ... C. ... D. ...').");
        }

        log.info("Parsed {} questions from PDF with {} warnings", parsedQuestions.size(), warnings.size());
        return new ParsedResult(parsedQuestions, warnings);
    }

    private Map<Integer, String> parseAnswerKeyTable(String fullText) {
        Map<Integer, String> answerMap = new HashMap<>();
        Matcher tableMatcher = ANSWER_TABLE_PATTERN.matcher(fullText);
        if (tableMatcher.find()) {
            String tableContent = fullText.substring(tableMatcher.start());
            Matcher entryMatcher = ANSWER_KEY_ENTRY_PATTERN.matcher(tableContent);
            while (entryMatcher.find()) {
                try {
                    int qNum = Integer.parseInt(entryMatcher.group(1));
                    String ans = entryMatcher.group(2).toUpperCase();
                    answerMap.put(qNum, ans);
                } catch (NumberFormatException ignored) {}
            }
        }
        return answerMap;
    }

    private CreateQuestionDTO parseQuestionBlock(
            String block,
            int questionNum,
            UUID subjectId,
            UUID topicId,
            Map<Integer, String> answerKeyTable,
            List<String> warnings) {

        // Find inline answer key if present in the block
        String correctAnswerLetter = null;
        Matcher inlineAnsMatcher = INLINE_ANSWER_PATTERN.matcher(block);
        if (inlineAnsMatcher.find()) {
            correctAnswerLetter = inlineAnsMatcher.group(1).toUpperCase();
        } else if (answerKeyTable.containsKey(questionNum)) {
            correctAnswerLetter = answerKeyTable.get(questionNum);
        }

        // Find positions of options A, B, C, D
        int[] optionPositions = findOptionPositions(block);
        int posA = optionPositions[0];
        int posB = optionPositions[1];
        int posC = optionPositions[2];
        int posD = optionPositions[3];

        if (posA == -1 || posB == -1 || posC == -1 || posD == -1) {
            warnings.add("Câu " + questionNum + ": Không đủ 4 phương án A, B, C, D. Bỏ qua câu hỏi này hoặc cần kiểm tra lại.");
            return null;
        }

        // Question content is before option A
        String questionStem = block.substring(0, posA).trim();
        if (questionStem.isEmpty()) {
            questionStem = "Câu " + questionNum;
        }

        // Extract option text
        String optAText = cleanOptionText(block.substring(posA + getPrefixLength(block, posA), posB));
        String optBText = cleanOptionText(block.substring(posB + getPrefixLength(block, posB), posC));
        String optCText = cleanOptionText(block.substring(posC + getPrefixLength(block, posC), posD));

        // Option D is from posD until inline answer or end of block
        int endD = block.length();
        if (inlineAnsMatcher.find(posD)) {
            endD = inlineAnsMatcher.start();
        }
        String optDText = cleanOptionText(block.substring(posD + getPrefixLength(block, posD), endD));

        // Check for asterisk-marked correct answer (e.g. *A. or A*. or *)
        if (correctAnswerLetter == null) {
            if (isMarkedWithAsterisk(block, posA, posB)) correctAnswerLetter = "A";
            else if (isMarkedWithAsterisk(block, posB, posC)) correctAnswerLetter = "B";
            else if (isMarkedWithAsterisk(block, posC, posD)) correctAnswerLetter = "C";
            else if (isMarkedWithAsterisk(block, posD, endD)) correctAnswerLetter = "D";
        }

        // Default to A if not found
        if (correctAnswerLetter == null) {
            correctAnswerLetter = "A";
            warnings.add("Câu " + questionNum + ": Không xác định được đáp án đúng, mặc định chọn A. Vui lòng kiểm tra lại.");
        }

        // Build 4 options
        List<CreateQuestionOptionDTO> options = new ArrayList<>();
        options.add(CreateQuestionOptionDTO.builder()
                .content(optAText)
                .isCorrect("A".equalsIgnoreCase(correctAnswerLetter))
                .orderIndex(0)
                .build());

        options.add(CreateQuestionOptionDTO.builder()
                .content(optBText)
                .isCorrect("B".equalsIgnoreCase(correctAnswerLetter))
                .orderIndex(1)
                .build());

        options.add(CreateQuestionOptionDTO.builder()
                .content(optCText)
                .isCorrect("C".equalsIgnoreCase(correctAnswerLetter))
                .orderIndex(2)
                .build());

        options.add(CreateQuestionOptionDTO.builder()
                .content(optDText)
                .isCorrect("D".equalsIgnoreCase(correctAnswerLetter))
                .orderIndex(3)
                .build());

        // Determine difficulty level
        DifficultyLevel difficulty = DifficultyLevel.EASY;
        String upperBlock = block.toUpperCase();
        if (upperBlock.contains("[VẬN DỤNG CAO]") || upperBlock.contains("[KHÓ]") || upperBlock.contains("[HARD]")) {
            difficulty = DifficultyLevel.HARD;
        } else if (upperBlock.contains("[VẬN DỤNG]") || upperBlock.contains("[TRUNG BÌNH]") || upperBlock.contains("[MEDIUM]")) {
            difficulty = DifficultyLevel.MEDIUM;
        }

        return CreateQuestionDTO.builder()
                .subjectId(subjectId)
                .topicId(topicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content(questionStem)
                .difficultyLevel(difficulty)
                .options(options)
                .build();
    }

    private int[] findOptionPositions(String block) {
        int[] positions = new int[]{-1, -1, -1, -1};
        char[] letters = new char[]{'A', 'B', 'C', 'D'};

        for (int i = 0; i < letters.length; i++) {
            char letter = letters[i];
            // Pattern to match A. or A) or A: or *A. or A*. at start of line or preceded by whitespace
            Pattern pattern = Pattern.compile("(?:^|[\\s\\n])(\\*?\\s*" + letter + "\\s*\\*?)[.:\\-\\)]");
            Matcher m = pattern.matcher(block);
            if (m.find()) {
                // If it matched with leading whitespace, adjust to the start of the letter
                int matchStart = m.start();
                while (matchStart < block.length() && Character.isWhitespace(block.charAt(matchStart))) {
                    matchStart++;
                }
                positions[i] = matchStart;
            }
        }

        // Ensure order posA < posB < posC < posD
        if (positions[0] != -1 && positions[1] != -1 && positions[2] != -1 && positions[3] != -1) {
            if (!(positions[0] < positions[1] && positions[1] < positions[2] && positions[2] < positions[3])) {
                return new int[]{-1, -1, -1, -1};
            }
        }

        return positions;
    }

    private int getPrefixLength(String block, int startPos) {
        // e.g. "A." -> 2 chars, "*A." -> 3 chars, "A) " -> 2 or 3
        int len = 0;
        while (startPos + len < block.length()) {
            char c = block.charAt(startPos + len);
            if (c == '.' || c == ')' || c == ':' || c == '-') {
                len++;
                break;
            }
            len++;
        }
        return len;
    }

    private String cleanOptionText(String raw) {
        String cleaned = raw.trim();
        // Remove trailing or leading asterisks
        cleaned = cleaned.replaceAll("^\\*+|\\*+$", "").trim();
        // Collapse multiple spaces/newlines into single space
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned;
    }

    private boolean isMarkedWithAsterisk(String block, int start, int end) {
        if (start < 0 || end > block.length() || start >= end) return false;
        String segment = block.substring(start, end);
        return segment.startsWith("*") || segment.contains(" *") || segment.contains("* ");
    }
}
