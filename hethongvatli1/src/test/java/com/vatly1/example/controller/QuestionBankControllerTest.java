package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vatly1.example.app.JwtAuthServiceApp;
import org.springframework.test.context.ActiveProfiles;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class QuestionBankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private String subjectId;
    private String topicId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();

        String topicName = "Topic QB " + System.currentTimeMillis();
        String topicContent = """
            {
                "topicName": "%s",
                "orderIndex": 1
            }
            """.formatted(topicName);
        String topicRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicContent))
            .andReturn().getResponse().getContentAsString();
        topicId = objectMapper.readTree(topicRes).get("data").get("topicId").asText();
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("QB-01: Admin tạo câu hỏi trắc nghiệm hợp lệ với 4 phương án thành công")
    void createQuestion_asAdmin_success() throws Exception {
        String questionContent = """
            {
                "subjectId": "%s",
                "topicId": "%s",
                "questionType": "MCQ_SINGLE",
                "content": "Gia tốc rơi tự do là bao nhiêu?",
                "difficultyLevel": "EASY",
                "options": [
                    {
                        "content": "9.8 m/s^2",
                        "isCorrect": true,
                        "orderIndex": 0
                    },
                    {
                        "content": "10 m/s^2",
                        "isCorrect": false,
                        "orderIndex": 1
                    },
                    {
                        "content": "9.78 m/s^2",
                        "isCorrect": false,
                        "orderIndex": 2
                    },
                    {
                        "content": "8.9 m/s^2",
                        "isCorrect": false,
                        "orderIndex": 3
                    }
                ]
            }
            """.formatted(subjectId, topicId);
            
        mockMvc.perform(post("/api/v1/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(questionContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content").value("Gia tốc rơi tự do là bao nhiêu?"))
            .andExpect(jsonPath("$.data.options.length()").value(4));
    }

    @Test
    @DisplayName("QB-02: Sinh viên không được phép tạo câu hỏi (403 Forbidden)")
    void createQuestion_asStudent_returns403() throws Exception {
        String questionContent = """
            {
                "subjectId": "%s",
                "topicId": "%s",
                "questionType": "MCQ_SINGLE",
                "content": "Câu hỏi test",
                "difficultyLevel": "EASY"
            }
            """.formatted(subjectId, topicId);
            
        mockMvc.perform(post("/api/v1/questions")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(questionContent))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("QB-03: Tạo câu hỏi MCQ_SINGLE với 0 đáp án đúng -> phải bị từ chối 400 Bad Request")
    void createQuestion_singleChoiceWithZeroCorrectAnswers_returns400() throws Exception {
        String questionContent = """
            {
                "subjectId": "%s",
                "topicId": "%s",
                "questionType": "MCQ_SINGLE",
                "content": "Câu hỏi không có đáp án đúng?",
                "difficultyLevel": "EASY",
                "options": [
                    { "content": "Đáp án A", "isCorrect": false, "orderIndex": 0 },
                    { "content": "Đáp án B", "isCorrect": false, "orderIndex": 1 },
                    { "content": "Đáp án C", "isCorrect": false, "orderIndex": 2 },
                    { "content": "Đáp án D", "isCorrect": false, "orderIndex": 3 }
                ]
            }
            """.formatted(subjectId, topicId);

        mockMvc.perform(post("/api/v1/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(questionContent))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QB-04: Tạo câu hỏi MCQ_SINGLE với 2 đáp án đúng trở lên -> phải bị từ chối 400 Bad Request")
    void createQuestion_singleChoiceWithMultipleCorrectAnswers_returns400() throws Exception {
        String questionContent = """
            {
                "subjectId": "%s",
                "topicId": "%s",
                "questionType": "MCQ_SINGLE",
                "content": "Câu hỏi có 2 đáp án đúng trong lựa chọn đơn?",
                "difficultyLevel": "EASY",
                "options": [
                    { "content": "Đáp án A", "isCorrect": true, "orderIndex": 0 },
                    { "content": "Đáp án B", "isCorrect": true, "orderIndex": 1 },
                    { "content": "Đáp án C", "isCorrect": false, "orderIndex": 2 },
                    { "content": "Đáp án D", "isCorrect": false, "orderIndex": 3 }
                ]
            }
            """.formatted(subjectId, topicId);

        mockMvc.perform(post("/api/v1/questions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(questionContent))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QB-05: Giảng viên/Admin tải file template Excel mẫu thành công (200 OK, .xlsx)")
    void downloadTemplate_asAdmin_returnsExcelFile() throws Exception {
        mockMvc.perform(get("/api/v1/questions/import-excel/template")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=mau_import_cau_hoi.xlsx"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("QB-06: Import Excel với file rỗng hoặc không phải file Excel -> trả về lỗi rõ ràng 400 Bad Request")
    void importQuestionsFromExcel_withEmptyOrInvalidExcel_returns400() throws Exception {
        // 1. File rỗng
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/questions/import-excel")
                .file(emptyFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        // 2. File không phải đuôi Excel
        MockMultipartFile notExcelFile = new MockMultipartFile(
                "file", "invalid.txt", "text/plain", "This is plain text".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/questions/import-excel")
                .file(notExcelFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QB-07: Import file Excel có nội dung thiếu phương án/đáp án đúng -> trả về warnings an toàn, không văng 500")
    void importQuestionsFromExcel_withIncompleteRows_returnsWarningsWithout500() throws Exception {
        byte[] excelBytes = createSampleExcelWorkbook(false);

        MockMultipartFile excelFile = new MockMultipartFile(
                "file", "unstructured.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/v1/questions/import-excel")
                .file(excelFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.totalParsed").value(0))
            .andExpect(jsonPath("$.data.totalImported").value(0))
            .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    @DisplayName("QB-08: Import file Excel chuẩn bóc tách thành công 2 câu hỏi kèm 4 phương án đầy đủ")
    void importQuestionsFromExcel_asAdmin_success() throws Exception {
        byte[] excelBytes = createSampleExcelWorkbook(true);

        MockMultipartFile excelFile = new MockMultipartFile(
                "file", "vatli_exam.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/v1/questions/import-excel")
                .file(excelFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.totalParsed").value(2))
            .andExpect(jsonPath("$.data.totalImported").value(2))
            .andExpect(jsonPath("$.data.questions[0].options.length()").value(4))
            .andExpect(jsonPath("$.data.questions[1].options.length()").value(4));
    }

    @Test
    @DisplayName("QB-09: Sinh viên import câu hỏi qua Excel bị chặn 403 Forbidden")
    void importQuestionsFromExcel_asStudent_returns403() throws Exception {
        MockMultipartFile dummyFile = new MockMultipartFile(
                "file", "dummy.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/questions/import-excel")
                .file(dummyFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    private byte[] createSampleExcelWorkbook(boolean validQuestions) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Questions");
            Row header = sheet.createRow(0);
            String[] headers = {"STT", "Nội dung câu hỏi", "Loại", "Mức độ nhận thức", "Mức độ khó", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D", "Đáp án đúng", "Giải thích"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            if (validQuestions) {
                Row r1 = sheet.createRow(1);
                r1.createCell(0).setCellValue(1);
                r1.createCell(1).setCellValue("Gia tốc rơi tự do xấp xỉ bằng bao nhiêu?");
                r1.createCell(2).setCellValue("MCQ_SINGLE");
                r1.createCell(3).setCellValue("NHAN_BIET");
                r1.createCell(4).setCellValue("EASY");
                r1.createCell(5).setCellValue("9.8 m/s^2");
                r1.createCell(6).setCellValue("10 m/s^2");
                r1.createCell(7).setCellValue("9.78 m/s^2");
                r1.createCell(8).setCellValue("8.9 m/s^2");
                r1.createCell(9).setCellValue("A");
                r1.createCell(10).setCellValue("Trọng trường Trái Đất");

                Row r2 = sheet.createRow(2);
                r2.createCell(0).setCellValue(2);
                r2.createCell(1).setCellValue("Đơn vị của lực trong hệ SI là gì?");
                r2.createCell(2).setCellValue("MCQ_SINGLE");
                r2.createCell(3).setCellValue("NHAN_BIET");
                r2.createCell(4).setCellValue("EASY");
                r2.createCell(5).setCellValue("Jun (J)");
                r2.createCell(6).setCellValue("Oát (W)");
                r2.createCell(7).setCellValue("Niu-tơn (N)");
                r2.createCell(8).setCellValue("Paxcan (Pa)");
                r2.createCell(9).setCellValue("C");
                r2.createCell(10).setCellValue("Đơn vị lực là Newton");
            } else {
                Row r1 = sheet.createRow(1);
                r1.createCell(0).setCellValue(1);
                r1.createCell(1).setCellValue("Thông báo lịch thi học kỳ");
                // Không có phương án A, B, C, D hay đáp án đúng
            }
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}