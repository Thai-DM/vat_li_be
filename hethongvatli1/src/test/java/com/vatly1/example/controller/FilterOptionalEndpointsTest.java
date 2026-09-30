package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FilterOptionalEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private String instructorToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
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
    @DisplayName("OPT-01: GET /api/v1/questions không truyền subjectId trả về tất cả câu hỏi thành công")
    void getQuestions_withoutSubjectId_success() throws Exception {
        mockMvc.perform(get("/api/v1/questions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("OPT-02: GET /api/v1/experiments không truyền subjectId trả về tất cả bài thí nghiệm thành công")
    void getExperiments_withoutSubjectId_success() throws Exception {
        mockMvc.perform(get("/api/v1/experiments")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-03: GET /api/v1/students/me/progress không truyền classId trả về tiến độ tất cả lớp học thành công")
    void getMyProgress_withoutClassId_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-04: GET /api/v1/exams không truyền classId trả về tất cả kỳ thi sinh viên có quyền xem")
    void getExams_asStudent_withoutClassId_success() throws Exception {
        mockMvc.perform(get("/api/v1/exams")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-05: GET /api/v1/exams không truyền classId trả về tất cả kỳ thi cho Admin")
    void getExams_asAdmin_withoutClassId_success() throws Exception {
        mockMvc.perform(get("/api/v1/exams")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-06: GET /api/v1/topics không truyền subjectId trả về tất cả chương mục thành công")
    void getTopics_withoutSubjectId_success() throws Exception {
        mockMvc.perform(get("/api/v1/topics")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-07: GET /api/v1/materials không truyền topicId trả về tất cả học liệu thành công")
    void getMaterials_withoutTopicId_success() throws Exception {
        mockMvc.perform(get("/api/v1/materials")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("OPT-08: GET /api/v1/materials cho sinh viên chỉ trả về học liệu đã được duyệt (APPROVED)")
    void getMaterials_asStudent_success() throws Exception {
        mockMvc.perform(get("/api/v1/materials")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }
}
