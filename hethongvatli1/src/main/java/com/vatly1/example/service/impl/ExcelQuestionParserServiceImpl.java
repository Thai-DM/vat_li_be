package com.vatly1.example.service.impl;

import com.vatly1.example.dto.request.CreateQuestionDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.service.IExcelQuestionParserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExcelQuestionParserServiceImpl implements IExcelQuestionParserService {

    @Override
    public ParsedResult parseQuestionsFromExcel(InputStream inputStream, UUID subjectId, UUID topicId) {
        List<CreateQuestionDTO> questions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                warnings.add("Tệp Excel không chứa bất kỳ trang tính (sheet) nào.");
                return new ParsedResult(questions, warnings);
            }

            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            if (lastRowNum < firstRowNum) {
                warnings.add("Trang tính rỗng, không có dữ liệu.");
                return new ParsedResult(questions, warnings);
            }

            // Tìm dòng header
            Row headerRow = sheet.getRow(firstRowNum);
            if (headerRow == null) {
                warnings.add("Không tìm thấy dòng tiêu đề trong tệp Excel.");
                return new ParsedResult(questions, warnings);
            }

            Map<String, Integer> colMap = buildColumnMapping(headerRow);

            for (int r = firstRowNum + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                String content = getCellValue(row, colMap.get("content"));
                if (content == null || content.isBlank()) {
                    // Dòng trống -> bỏ qua
                    continue;
                }

                String optA = getCellValue(row, colMap.get("optA"));
                String optB = getCellValue(row, colMap.get("optB"));
                String optC = getCellValue(row, colMap.get("optC"));
                String optD = getCellValue(row, colMap.get("optD"));
                String correctRaw = getCellValue(row, colMap.get("correct"));
                String diffRaw = getCellValue(row, colMap.get("difficulty"));
                String cogRaw = getCellValue(row, colMap.get("cognitive"));
                String explanation = getCellValue(row, colMap.get("explanation"));
                String qTypeRaw = getCellValue(row, colMap.get("questionType"));

                int rowNumDisplay = r + 1;

                if (optA == null && optB == null) {
                    warnings.add("Dòng " + rowNumDisplay + ": Thiếu các phương án trả lời.");
                    continue;
                }

                if (correctRaw == null || correctRaw.isBlank()) {
                    warnings.add("Dòng " + rowNumDisplay + ": Chưa chỉ định đáp án đúng.");
                    continue;
                }

                DifficultyLevel difficultyLevel = parseDifficultyLevel(diffRaw);
                String cognitiveLevel = parseCognitiveLevel(cogRaw);
                QuestionType questionType = parseQuestionType(qTypeRaw);

                // Xây dựng danh sách phương án
                List<CreateQuestionDTO.CreateQuestionOptionDTO> options = new ArrayList<>();
                String[] rawOptions = {optA, optB, optC, optD};
                char[] optionLetters = {'A', 'B', 'C', 'D'};

                Set<Character> correctLetters = parseCorrectLetters(correctRaw);

                for (int i = 0; i < rawOptions.length; i++) {
                    String optText = rawOptions[i];
                    if (optText != null && !optText.isBlank()) {
                        char letter = optionLetters[i];
                        boolean isCorrect = correctLetters.contains(letter)
                                || (correctRaw.equalsIgnoreCase(optText.trim()));

                        options.add(CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content(optText.trim())
                                .isCorrect(isCorrect)
                                .orderIndex(i)
                                .explanation(explanation)
                                .build());
                    }
                }

                long correctCount = options.stream().filter(CreateQuestionDTO.CreateQuestionOptionDTO::getIsCorrect).count();

                if (options.size() < 2) {
                    warnings.add("Dòng " + rowNumDisplay + ": Câu hỏi phải có ít nhất 2 phương án trả lời.");
                    continue;
                }

                if (correctCount == 0) {
                    warnings.add("Dòng " + rowNumDisplay + ": Không xác định được đáp án đúng khớp với phương án A/B/C/D.");
                    continue;
                }

                if (questionType == QuestionType.MCQ_SINGLE && correctCount > 1) {
                    warnings.add("Dòng " + rowNumDisplay + ": Câu hỏi trắc nghiệm một lựa chọn (MCQ_SINGLE) có nhiều hơn 1 đáp án đúng.");
                    continue;
                }

                CreateQuestionDTO dto = CreateQuestionDTO.builder()
                        .subjectId(subjectId)
                        .topicId(topicId)
                        .questionType(questionType)
                        .content(content.trim())
                        .difficultyLevel(difficultyLevel)
                        .cognitiveLevel(cognitiveLevel)
                        .options(options)
                        .build();

                questions.add(dto);
            }

        } catch (Exception e) {
            log.error("Lỗi khi đọc file Excel: ", e);
            warnings.add("Định dạng file Excel không hợp lệ hoặc bị lỗi: " + e.getMessage());
        }

        return new ParsedResult(questions, warnings);
    }

    private Map<String, Integer> buildColumnMapping(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();

        // Gán vị trí mặc định
        map.put("stt", 0);
        map.put("content", 1);
        map.put("questionType", 2);
        map.put("cognitive", 3);
        map.put("difficulty", 4);
        map.put("optA", 5);
        map.put("optB", 6);
        map.put("optC", 7);
        map.put("optD", 8);
        map.put("correct", 9);
        map.put("explanation", 10);

        // Duyệt header để override theo tên cột
        for (Cell cell : headerRow) {
            String val = getCellValueAsString(cell).trim().toLowerCase();
            String norm = deAccent(val);
            int idx = cell.getColumnIndex();

            if (norm.contains("noi dung") || norm.contains("cau hoi") || norm.contains("question") || norm.contains("content")) {
                map.put("content", idx);
            } else if (norm.contains("loai") || norm.contains("type")) {
                map.put("questionType", idx);
            } else if (norm.contains("nhan thuc") || norm.contains("bloom") || norm.contains("cognitive")) {
                map.put("cognitive", idx);
            } else if (norm.contains("do kho") || norm.contains("muc do kho") || norm.contains("difficulty")) {
                map.put("difficulty", idx);
            } else if (norm.equals("dap an a") || norm.equals("phuong an a") || norm.equals("a") || norm.equals("option a")) {
                map.put("optA", idx);
            } else if (norm.equals("dap an b") || norm.equals("phuong an b") || norm.equals("b") || norm.equals("option b")) {
                map.put("optB", idx);
            } else if (norm.equals("dap an c") || norm.equals("phuong an c") || norm.equals("c") || norm.equals("option c")) {
                map.put("optC", idx);
            } else if (norm.equals("dap an d") || norm.equals("phuong an d") || norm.equals("d") || norm.equals("option d")) {
                map.put("optD", idx);
            } else if (norm.contains("dap an dung") || norm.contains("correct") || norm.equals("dap an")) {
                map.put("correct", idx);
            } else if (norm.contains("giai thich") || norm.contains("explanation")) {
                map.put("explanation", idx);
            }
        }

        return map;
    }

    private String getCellValue(Row row, Integer colIndex) {
        if (colIndex == null || colIndex < 0) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        String val = getCellValueAsString(cell).trim();
        return val.isEmpty() ? null : val;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == (long) num) {
                    yield String.valueOf((long) num);
                } else {
                    yield String.valueOf(num);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Set<Character> parseCorrectLetters(String raw) {
        Set<Character> set = new HashSet<>();
        if (raw == null) return set;
        String upper = raw.toUpperCase();
        for (char ch : upper.toCharArray()) {
            if (ch >= 'A' && ch <= 'D') {
                set.add(ch);
            }
        }
        return set;
    }

    private DifficultyLevel parseDifficultyLevel(String raw) {
        if (raw == null || raw.isBlank()) return DifficultyLevel.MEDIUM;
        String norm = deAccent(raw.trim().toLowerCase());
        if (norm.contains("kho") || norm.contains("hard") || norm.contains("expert")) {
            return DifficultyLevel.HARD;
        }
        if (norm.contains("de") || norm.contains("easy")) {
            return DifficultyLevel.EASY;
        }
        return DifficultyLevel.MEDIUM;
    }

    private String parseCognitiveLevel(String raw) {
        if (raw == null || raw.isBlank()) return "THONG_HIEU";
        String norm = deAccent(raw.trim().toLowerCase());
        if (norm.contains("van dung cao")) {
            return "VAN_DUNG_CAO";
        }
        if (norm.contains("van dung")) {
            return "VAN_DUNG";
        }
        if (norm.contains("nhan biet")) {
            return "NHAN_BIET";
        }
        return "THONG_HIEU";
    }

    private QuestionType parseQuestionType(String raw) {
        if (raw == null || raw.isBlank()) return QuestionType.MCQ_SINGLE;
        String norm = raw.trim().toUpperCase();
        if (norm.contains("MULTI") || norm.contains("NHIEU_DAP_AN")) {
            return QuestionType.MCQ_MULTI;
        }
        return QuestionType.MCQ_SINGLE;
    }

    private String deAccent(String str) {
        if (str == null) return "";
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    @Override
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Mau_Cau_Hoi");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Data Style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.cloneStyleFrom(dataStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header labels
            String[] headers = {
                    "STT",
                    "Nội dung câu hỏi (*)",
                    "Loại câu hỏi",
                    "Mức độ nhận thức (Bloom)",
                    "Mức độ khó",
                    "Đáp án A (*)",
                    "Đáp án B (*)",
                    "Đáp án C",
                    "Đáp án D",
                    "Đáp án đúng (*)",
                    "Giải thích chi tiết"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dữ liệu mẫu
            Object[][] sampleData = {
                    {1, "Gia tốc rơi tự do tại mặt đất xấp xỉ bằng bao nhiêu?", "MCQ_SINGLE", "NHAN_BIET", "EASY", "9.8 m/s^2", "10 m/s^2", "9.78 m/s^2", "8.9 m/s^2", "A", "Gia tốc trọng trường tiêu chuẩn g ≈ 9.80665 m/s²."},
                    {2, "Đơn vị đo lực trong hệ đo lường quốc tế SI là gì?", "MCQ_SINGLE", "NHAN_BIET", "EASY", "Jun (J)", "Oát (W)", "Niu-tơn (N)", "Paxcan (Pa)", "C", "Đơn vị lực trong SI là Newton (N)."},
                    {3, "Một vật có khối lượng m = 2 kg đang chuyển động với vận tốc v = 3 m/s. Động năng của vật là:", "MCQ_SINGLE", "THONG_HIEU", "MEDIUM", "6 J", "9 J", "18 J", "3 J", "B", "Động năng Wd = 1/2 * m * v^2 = 1/2 * 2 * 3^2 = 9 J."},
                    {4, "Trong chuyển động thẳng biến đổi đều, công thức liên hệ giữa vận tốc, gia tốc và quãng đường là:", "MCQ_SINGLE", "THONG_HIEU", "MEDIUM", "v^2 - v0^2 = 2as", "v - v0 = 2as", "v^2 + v0^2 = 2as", "s = v*t + a*t^2", "A", "Công thức độc lập thời gian: v^2 - v0^2 = 2as."}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(22);
                Object[] rowData = sampleData[r];
                for (int c = 0; c < rowData.length; c++) {
                    Cell cell = row.createCell(c);
                    if (rowData[c] instanceof Number) {
                        cell.setCellValue(((Number) rowData[c]).doubleValue());
                        cell.setCellStyle(centerStyle);
                    } else {
                        cell.setCellValue(rowData[c].toString());
                        cell.setCellStyle((c == 2 || c == 3 || c == 4 || c == 9) ? centerStyle : dataStyle);
                    }
                }
            }

            // Tự động căn chỉnh độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3000));
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Lỗi khi sinh file mẫu Excel: ", e);
            throw new RuntimeException("Không thể tạo file mẫu Excel: " + e.getMessage(), e);
        }
    }
}
