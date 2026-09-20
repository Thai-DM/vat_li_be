package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.entity.AuditLog;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.User;
import com.vatly1.example.repository.ActivityLogRepository;
import com.vatly1.example.repository.AuditLogRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ActivityAndAuditLogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private String adminToken;
    private String instructorToken;
    private String studentToken;
    private String classId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        Class targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()) && "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();
        classId = targetClass.getClassId().toString();
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

    private boolean waitForCondition(Supplier<Boolean> condition, int timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.get();
    }

    @Test
    @DisplayName("LOG-01: Sau startAttempt -> activity_log tự tạo với action=START_EXAM")
    void testLog01_StartExamCreatesActivityLog() throws Exception {
        String payload = """
            {
                "classId": "%s",
                "title": "Bài test AOP Log %s",
                "examType": "QUIZ",
                "durationMinutes": 30
            }
            """.formatted(classId, UUID.randomUUID());

        String res = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(res).get("data").get("examId").asText();

        // Student starts attempt
        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());

        // Wait for async log
        boolean found = waitForCondition(() -> {
            List<ActivityLog> logs = activityLogRepository.findAll();
            return logs.stream().anyMatch(l -> "START_EXAM".equals(l.getActionType())
                    && UUID.fromString(examId).equals(l.getObjectId()));
        }, 3000);

        assertTrue(found, "ActivityLog with action=START_EXAM must be recorded asynchronously");
    }

    @Test
    @DisplayName("LOG-02: Sau submitAttempt -> activity_log tự tạo với action=SUBMIT_EXAM")
    void testLog02_SubmitExamCreatesActivityLog() throws Exception {
        String payload = """
            {
                "classId": "%s",
                "title": "Bài test AOP Submit Log %s",
                "examType": "QUIZ",
                "durationMinutes": 30
            }
            """.formatted(classId, UUID.randomUUID());

        String res = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String examId = objectMapper.readTree(res).get("data").get("examId").asText();

        // Student starts attempt
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Student submits attempt
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Wait for async log
        boolean found = waitForCondition(() -> {
            List<ActivityLog> logs = activityLogRepository.findAll();
            return logs.stream().anyMatch(l -> "SUBMIT_EXAM".equals(l.getActionType())
                    && UUID.fromString(attemptId).equals(l.getObjectId()));
        }, 3000);

        assertTrue(found, "ActivityLog with action=SUBMIT_EXAM must be recorded asynchronously");
    }

    @Test
    @DisplayName("LOG-05: Sau adminUpdateUser đổi role -> audit_log tự tạo với action=CHANGE_ROLE")
    void testLog05_ChangeRoleCreatesAuditLog() throws Exception {
        User target = userRepository.findByUsername("ta_hung");
        assertNotNull(target);

        String updateBody = "{\"role\":\"INSTRUCTOR\"}";
        mockMvc.perform(put("/api/v1/users/admin/users/" + target.getUserId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean found = auditLogs.stream().anyMatch(l -> "CHANGE_ROLE".equals(l.getAction())
                && target.getUserId().equals(l.getEntityId())
                && "USER".equals(l.getEntity()));

        assertTrue(found, "AuditLog with action=CHANGE_ROLE must be recorded");
    }

    @Test
    @DisplayName("LOG-05-STATUS: Sau adminUpdateUserStatus đổi status -> audit_log tự tạo với action=CHANGE_STATUS")
    void testLog05_ChangeStatusCreatesAuditLog() throws Exception {
        User target = userRepository.findByUsername("sv_cuong");
        assertNotNull(target);

        String updateBody = "{\"status\":\"LOCKED\"}";
        mockMvc.perform(put("/api/v1/users/admin/users/" + target.getUserId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean found = auditLogs.stream().anyMatch(l -> "CHANGE_STATUS".equals(l.getAction())
                && target.getUserId().equals(l.getEntityId())
                && "USER".equals(l.getEntity()));

        assertTrue(found, "AuditLog with action=CHANGE_STATUS must be recorded");
    }

    @Test
    @DisplayName("LOG-03: Admin GET /admin/activity-logs -> 200, trả danh sách phân trang")
    void testLog03_AdminGetActivityLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/activity-logs?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("LOG-04: Non-admin (student) GET /admin/activity-logs -> 403 Forbidden")
    void testLog04_NonAdminGetActivityLogsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("LOG-06: Admin GET /admin/audit-logs filter by entity -> 200")
    void testLog06_AdminGetAuditLogsFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs?entity=USER&page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("LOG-07: SV GET /students/me/activity-logs -> 200, chỉ thấy của mình")
    void testLog07_StudentGetMyActivityLogs() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/activity-logs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("LOG-08: GV GET /classes/{id}/activity-logs -> 200")
    void testLog08_InstructorGetClassActivityLogs() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + classId + "/activity-logs")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}