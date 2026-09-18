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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
    @DisplayName("QB-05: Import PDF với file rỗng hoặc không phải PDF -> trả về lỗi rõ ràng 400, không crash")
    void importQuestionsFromPdf_withEmptyOrInvalidPdf_returns400() throws Exception {
        // 1. File rỗng
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/questions/import-pdf")
                .file(emptyFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        // 2. File không phải PDF (corrupted byte stream)
        MockMultipartFile corruptedFile = new MockMultipartFile(
                "file", "invalid.pdf", "application/pdf", "Day la file text binh thuong khong phai header PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/questions/import-pdf")
                .file(corruptedFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QB-06: Import PDF với nội dung không đúng cấu trúc câu hỏi -> totalImported = 0, không văng 500")
    void importQuestionsFromPdf_withUnstructuredContent_returnsZeroImportedWithout500() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Thong bao lich thi giua ky mon Vat ly dai cuong.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Tat ca sinh vien chu y co mat luc 7h30 sang thu Bay tai phong A102.");
                cs.newLineAtOffset(0, -20);
                cs.showText("Mang theo the sinh vien va may tinh bo tui.");
                cs.endText();
            }
            doc.save(baos);
        }

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "announcement.pdf", "application/pdf", baos.toByteArray()
        );

        mockMvc.perform(multipart("/api/v1/questions/import-pdf")
                .file(pdfFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.totalParsed").value(0))
            .andExpect(jsonPath("$.data.totalImported").value(0))
            .andExpect(jsonPath("$.data.warnings").isArray());
    }

    @Test
    @DisplayName("QB-07: Import đề thi chuẩn từ PDF bóc tách thành công 2 câu hỏi kèm 4 phương án")
    void importQuestionsFromPdf_asAdmin_success() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Cau 1: Gia toc trong truong tai mat dat xap xi bang bao nhieu?");
                cs.newLineAtOffset(0, -20);
                cs.showText("A. 9.8 m/s^2");
                cs.newLineAtOffset(0, -20);
                cs.showText("B. 10 m/s^2");
                cs.newLineAtOffset(0, -20);
                cs.showText("C. 9.78 m/s^2");
                cs.newLineAtOffset(0, -20);
                cs.showText("D. 8.9 m/s^2");
                cs.newLineAtOffset(0, -20);
                cs.showText("Dap an: A");
                cs.newLineAtOffset(0, -30);
                cs.showText("Cau 2: Don vi cua luc trong he do luong SI la gi?");
                cs.newLineAtOffset(0, -20);
                cs.showText("A. Jun (J)");
                cs.newLineAtOffset(0, -20);
                cs.showText("B. Oat (W)");
                cs.newLineAtOffset(0, -20);
                cs.showText("C. Niu-ton (N)");
                cs.newLineAtOffset(0, -20);
                cs.showText("D. Paxcan (Pa)");
                cs.newLineAtOffset(0, -20);
                cs.showText("Dap an: C");
                cs.endText();
            }
            doc.save(baos);
        }

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "vatli_exam.pdf",
                "application/pdf",
                baos.toByteArray()
        );

        mockMvc.perform(multipart("/api/v1/questions/import-pdf")
                .file(pdfFile)
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
    @DisplayName("QB-08: Sinh viên import đề thi PDF bị chặn 403 Forbidden")
    void importQuestionsFromPdf_asStudent_returns403() throws Exception {
        MockMultipartFile dummyFile = new MockMultipartFile(
                "file",
                "dummy.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/questions/import-pdf")
                .file(dummyFile)
                .param("subjectId", subjectId)
                .param("topicId", topicId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }
}
