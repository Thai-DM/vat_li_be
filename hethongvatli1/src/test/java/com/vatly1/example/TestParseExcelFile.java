package com.vatly1.example;

import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.service.IExcelQuestionParserService;
import com.vatly1.example.service.impl.ExcelQuestionParserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TestParseExcelFile {

    @Test
    @DisplayName("Parse actual file D:\\vatli1\\mau-nhap-cau-hoi (3).xlsx should accurately map content, Bloom, difficulty, and answers")
    public void testParseActualFile() throws Exception {
        File file = new File("D:\\vatli1\\mau-nhap-cau-hoi (3).xlsx");
        assertTrue(file.exists(), "File D:\\vatli1\\mau-nhap-cau-hoi (3).xlsx must exist");

        ExcelQuestionParserServiceImpl parser = new ExcelQuestionParserServiceImpl();
        try (FileInputStream fis = new FileInputStream(file)) {
            IExcelQuestionParserService.ParsedResult result = parser.parseQuestionsFromExcel(fis, UUID.randomUUID(), UUID.randomUUID());
            
            assertEquals(4, result.getQuestions().size(), "Must parse exactly 4 questions");
            assertTrue(result.getWarnings().isEmpty(), "Warnings must be empty for standard template");

            // Q1
            CreateQuestionDTO q1 = result.getQuestions().get(0);
            assertEquals("Gia tốc rơi tự do tại mặt đất xấp xỉ bằng bao nhiêu?", q1.getContent());
            assertEquals(DifficultyLevel.EASY, q1.getDifficultyLevel());
            assertEquals("NHAN_BIET", q1.getCognitiveLevel());
            assertEquals(QuestionType.MCQ_SINGLE, q1.getQuestionType());
            assertEquals(4, q1.getOptions().size());
            assertEquals("9.8 m/s^2", q1.getOptions().get(0).getContent());
            assertTrue(q1.getOptions().get(0).getIsCorrect(), "Option A must be correct");
            assertFalse(q1.getOptions().get(1).getIsCorrect());

            // Q2
            CreateQuestionDTO q2 = result.getQuestions().get(1);
            assertEquals("Đơn vị đo lực trong hệ đo lường quốc tế SI là gì?", q2.getContent());
            assertEquals(DifficultyLevel.EASY, q2.getDifficultyLevel());
            assertEquals("NHAN_BIET", q2.getCognitiveLevel());
            assertEquals("Niu-tơn (N)", q2.getOptions().get(2).getContent());
            assertTrue(q2.getOptions().get(2).getIsCorrect(), "Option C must be correct");

            // Q3
            CreateQuestionDTO q3 = result.getQuestions().get(2);
            assertEquals("Một vật có khối lượng m = 2 kg đang chuyển động với vận tốc v = 3 m/s. Động năng của vật là:", q3.getContent());
            assertEquals(DifficultyLevel.MEDIUM, q3.getDifficultyLevel());
            assertEquals("THONG_HIEU", q3.getCognitiveLevel());
            assertEquals("9 J", q3.getOptions().get(1).getContent());
            assertTrue(q3.getOptions().get(1).getIsCorrect(), "Option B must be correct");

            // Q4
            CreateQuestionDTO q4 = result.getQuestions().get(3);
            assertEquals("Trong chuyển động thẳng biến đổi đều, công thức liên hệ giữa vận tốc, gia tốc và quãng đường là:", q4.getContent());
            assertEquals(DifficultyLevel.MEDIUM, q4.getDifficultyLevel());
            assertEquals("THONG_HIEU", q4.getCognitiveLevel());
            assertEquals("v^2 - v0^2 = 2as", q4.getOptions().get(0).getContent());
            assertTrue(q4.getOptions().get(0).getIsCorrect(), "Option A must be correct");
        }
    }

    @Test
    @DisplayName("Generated template should parse successfully with both MCQ_SINGLE and FILL_BLANK questions")
    public void testParseGeneratedTemplate() throws Exception {
        ExcelQuestionParserServiceImpl parser = new ExcelQuestionParserServiceImpl();
        byte[] templateBytes = parser.generateTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(templateBytes)) {
            IExcelQuestionParserService.ParsedResult result = parser.parseQuestionsFromExcel(bais, UUID.randomUUID(), UUID.randomUUID());

            assertEquals(5, result.getQuestions().size(), "Must parse 5 questions (4 MCQ_SINGLE + 1 FILL_BLANK)");
            assertTrue(result.getWarnings().isEmpty(), "Generated template should have no warnings: " + result.getWarnings());

            // Verify question 5 is FILL_BLANK
            CreateQuestionDTO q5 = result.getQuestions().get(4);
            assertEquals(QuestionType.FILL_BLANK, q5.getQuestionType());
            assertNotNull(q5.getAcceptedAnswers());
            assertEquals(2, q5.getAcceptedAnswers().size());
            assertTrue(q5.getAcceptedAnswers().contains("N"));
            assertTrue(q5.getAcceptedAnswers().contains("Newton"));
            assertNull(q5.getOptions());
        }
    }
}
