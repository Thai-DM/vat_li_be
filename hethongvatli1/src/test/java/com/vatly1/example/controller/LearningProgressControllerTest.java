package com.vatly1.example.controller;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LearningProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private String adminToken;
    private String studentToken;
    private String subjectId;
    private String classId;
    private String topicId;
    private UUID unenrolledClassId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");

        Class c1 = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();
        classId = c1.getClassId().toString();
        subjectId = c1.getSubjectId().toString();

        String topicName = "Topic Progress " + System.currentTimeMillis();
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

        // Tạo 1 lớp riêng mà sinh viên sv_an hoàn toàn không ghi danh
        Class unenrolledClass = classRepository.save(Class.builder()
                .subjectId(UUID.fromString(subjectId))
                .classCode("UNENROLLED-CLASS-" + UUID.randomUUID())
                .status(ClassStatus.ACTIVE)
                .maxStudents(30)
                .build());
        unenrolledClassId = unenrolledClass.getClassId();
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
    @DisplayName("LP-01: Cập nhật tiến độ bình thường (50%) -> status = IN_PROGRESS")
    void updateProgress_asStudent_success() throws Exception {
        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": 50.0
            }
            """.formatted(classId, topicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progressPercent").value(50.0))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("LP-02: progressPercent = 100 -> status = COMPLETED")
    void updateProgress_progressPercent100_statusCompleted() throws Exception {
        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": 100.0
            }
            """.formatted(classId, topicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progressPercent").value(100.0))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("LP-03: progressPercent = 0 -> status = NOT_STARTED")
    void updateProgress_progressPercent0_statusNotStarted() throws Exception {
        // Topic mới chưa từng có progress
        String topicName = "Topic Zero Progress " + System.currentTimeMillis();
        String topicContent = """
            {
                "topicName": "%s",
                "orderIndex": 2
            }
            """.formatted(topicName);
        String topicRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicContent))
            .andReturn().getResponse().getContentAsString();
        String newTopicId = objectMapper.readTree(topicRes).get("data").get("topicId").asText();

        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": 0.0
            }
            """.formatted(classId, newTopicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progressPercent").value(0.0))
            .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("LP-04: progressPercent âm (-1) -> phải bị từ chối 400 Bad Request")
    void updateProgress_progressPercentNegative_returns400() throws Exception {
        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": -1.0
            }
            """.formatted(classId, topicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("LP-05: progressPercent vượt quá 100 (101) -> phải bị từ chối 400 Bad Request")
    void updateProgress_progressPercentGreaterThan100_returns400() throws Exception {
        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": 101.0
            }
            """.formatted(classId, topicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("LP-06: Cập nhật tiến độ cho lớp mà sinh viên chưa ghi danh -> phải bị từ chối (403 hoặc 400)")
    void updateProgress_forUnenrolledClass_returnsError() throws Exception {
        String progressContent = """
            {
                "classId": "%s",
                "topicId": "%s",
                "progressPercent": 60.0
            }
            """.formatted(unenrolledClassId, topicId);
            
        mockMvc.perform(put("/api/v1/students/me/progress")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressContent))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PROG-01: GV xem tiến độ cả lớp -> 200 OK và danh sách tiến độ")
    void getProgressByClass_asAssignedInstructor_success() throws Exception {
        String instructorToken = signin("gv_nguyen", "gv_nguyen123456");

        mockMvc.perform(get("/api/v1/classes/" + classId + "/progress")
                .header("Authorization", "Bearer " + instructorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PROG-02: GV xem lớp không tồn tại -> 404 Not Found")
    void getProgressByClass_nonExistentClass_returns404() throws Exception {
        String instructorToken = signin("gv_nguyen", "gv_nguyen123456");

        mockMvc.perform(get("/api/v1/classes/" + UUID.randomUUID() + "/progress")
                .header("Authorization", "Bearer " + instructorToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PROG-03: IDOR - GV không phụ trách lớp truy cập tiến độ lớp -> 403 Forbidden")
    void getProgressByClass_unassignedInstructor_returns403() throws Exception {
        String otherInstructorToken = signin("gv_tran", "gv_tran123456");

        mockMvc.perform(get("/api/v1/classes/" + classId + "/progress")
                .header("Authorization", "Bearer " + otherInstructorToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PROG-04: SV xem tiến độ cá nhân theo lớp -> 200 OK")
    void getMyProgress_asEnrolledStudent_success() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/progress?classId=" + classId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PROG-04-IDOR: SV xem tiến độ cá nhân ở lớp chưa ghi danh -> 403 Forbidden")
    void getMyProgress_unenrolledStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/progress?classId=" + unenrolledClassId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }
}
