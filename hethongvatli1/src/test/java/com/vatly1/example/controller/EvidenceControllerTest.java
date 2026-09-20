package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.EvidenceRepository;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.EvidenceSourceType;
import com.vatly1.example.entity.enums.SubmissionStatus;
import com.vatly1.example.repository.EvidenceRepositoryJpaRepo;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EvidenceRepositoryJpaRepo evidenceRepository;

    @Autowired
    private ExperimentSubmissionRepository experimentSubmissionRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private String instructorToken;
    private String studentToken;
    private String studentBToken;
    private String classId;
    private UUID studentAId;

    @BeforeEach
    void setUp() throws Exception {
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");
        studentBToken = signin("sv_binh", "sv_binh123456");

        User svAn = userRepository.findByUsername("sv_an");
        studentAId = svAn.getUserId();

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
    @DisplayName("EVD-01: SV xem danh mục minh chứng của mình -> 200 OK")
    void testEvd01_GetMyEvidenceSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/evidence")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("EVD-02: IDOR - SV B xem minh chứng của SV A -> 403 Forbidden")
    void testEvd02_StudentCannotViewOtherStudentEvidence() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + studentAId + "/evidence")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EVD-03: GV xem minh chứng của SV lớp mình phụ trách -> 200 OK")
    void testEvd03_InstructorViewsStudentEvidenceSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + studentAId + "/evidence")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("EVD-04: Submit bài thi -> Minh chứng tự động được ghi nhận (source_type = EXAM)")
    void testEvd04_SubmitExamGeneratesEvidence() throws Exception {
        String payload = """
            {
                "classId": "%s",
                "title": "Exam For Evidence %s",
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

        // Wait for async evidence creation
        boolean found = waitForCondition(() -> {
            List<EvidenceRepository> list = evidenceRepository.findByStudentIdOrderByCreatedAtDesc(studentAId);
            return list.stream().anyMatch(e -> e.getSourceType() == EvidenceSourceType.EXAM
                    && UUID.fromString(attemptId).equals(e.getSourceId()));
        }, 3000);

        assertTrue(found, "Evidence for exam attempt must be automatically recorded");
    }

    @Test
    @DisplayName("EVD-05: GV xác nhận bài lab -> Minh chứng tự động được ghi nhận (source_type = EXPERIMENT)")
    void testEvd05_ConfirmLabGeneratesEvidence() throws Exception {
        // Tạo submission giả định cho sv_an
        ExperimentSubmission submission = ExperimentSubmission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(studentAId)
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .evidenceUrl("https://example.com/lab-report.pdf")
                .build();
        submission = experimentSubmissionRepository.save(submission);

        // GV xác nhận bài nộp
        String confirmBody = "{\"note\":\"Xác nhận đạt yêu cầu thí nghiệm\"}";
        mockMvc.perform(post("/api/v1/experiments/submissions/" + submission.getSubmissionId() + "/confirmation")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());

        // Kiểm tra evidence được tạo
        final UUID subId = submission.getSubmissionId();
        boolean found = waitForCondition(() -> {
            List<EvidenceRepository> list = evidenceRepository.findByStudentIdOrderByCreatedAtDesc(studentAId);
            return list.stream().anyMatch(e -> e.getSourceType() == EvidenceSourceType.EXPERIMENT
                    && subId.equals(e.getSourceId()));
        }, 3000);

        assertTrue(found, "Evidence for experiment confirmation must be recorded");
    }

    @Test
    @DisplayName("EVD-06: GV xem tổng hợp minh chứng cả lớp -> 200 OK")
    void testEvd06_GetClassEvidenceSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + classId + "/evidence")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}