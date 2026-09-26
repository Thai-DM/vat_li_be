package com.vatly1.example.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.vatly1.example.app.JwtAuthServiceApp;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String instructorToken;
    private String studentToken;
    private String classId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");

        // Get classId of instructor
        String classesRes = mockMvc.perform(get("/api/v1/classes?page=0&size=1")
                .header("Authorization", "Bearer " + instructorToken))
            .andReturn().getResponse().getContentAsString();
        JsonNode classContent = objectMapper.readTree(classesRes).get("data").get("content");
        if (classContent != null && classContent.size() > 0) {
            classId = classContent.get(0).get("classId").asText();
        }
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
    @DisplayName("Lấy danh sách thông báo của sinh viên - Phân trang")
    void getMyNotifications_asStudent_success() throws Exception {
        mockMvc.perform(get("/api/v1/notifications?page=0&size=10")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("Lấy tóm tắt số thông báo chưa đọc - Cho icon chuông")
    void getNotificationSummary_asStudent_success() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/summary")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", notNullValue()))
            .andExpect(jsonPath("$.data.latestNotifications").isArray());
    }

    @Test
    @DisplayName("Giảng viên gửi thông báo đến cả lớp - Thành công")
    void sendNotificationToClass_asInstructor_success() throws Exception {
        if (classId == null) return;

        String bodyContent = """
            {
                "title": "Nhắc nhở nộp bài thí nghiệm ảo số 1",
                "content": "Hạn chót nộp bài thí nghiệm là 23:59 Chủ Nhật tuần này. Các em lưu ý nộp đầy đủ file minh chứng.",
                "type": "ANNOUNCEMENT"
            }
            """;

        mockMvc.perform(post("/api/v1/notifications/classes/" + classId)
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sentCount", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Sinh viên không có quyền gửi thông báo tới lớp - 403 Forbidden")
    void sendNotificationToClass_asStudent_forbidden() throws Exception {
        if (classId == null) return;

        String bodyContent = """
            {
                "title": "Sinh viên gửi thông báo",
                "content": "Nội dung trái phép",
                "type": "ANNOUNCEMENT"
            }
            """;

        mockMvc.perform(post("/api/v1/notifications/classes/" + classId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyContent))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sinh viên kích hoạt sinh thông báo nhắc lịch học")
    void generateScheduleReminders_asStudent_success() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/reminders/generate")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.generatedReminders", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Đánh dấu tất cả thông báo là đã đọc - Thành công")
    void markAllAsRead_asStudent_success() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());
    }
}
