package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.ExperimentSubmission;
import com.vatly1.example.entity.enums.SubmissionStatus;
import com.vatly1.example.repository.ExperimentConfirmationRepository;
import com.vatly1.example.repository.ExperimentSubmissionRepository;
import org.junit.jupiter.api.Assertions;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExperimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExperimentSubmissionRepository experimentSubmissionRepository;

    @Autowired
    private ExperimentConfirmationRepository experimentConfirmationRepository;

    @Autowired
    private com.vatly1.example.repository.ExperimentRepository experimentRepository;

    @Autowired
    private com.vatly1.example.repository.ExperimentRubricRepository experimentRubricRepository;

    @Autowired
    private com.vatly1.example.repository.ExperimentAssignmentRepository experimentAssignmentRepository;

    @Autowired
    private com.vatly1.example.repository.IUserRepository userRepository;

    private String adminToken;
    private String instructorToken;
    private String taToken;
    private String studentToken;
    private String studentBinhToken;
    private String subjectId;
    private String classId;
    private String experimentId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        taToken = signin("ta_hung", "ta_hung123456");
        studentToken = signin("sv_an", "sv_an123456");
        studentBinhToken = signin("sv_binh", "sv_binh123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();

        // Get student's enrolled class
        String myClassesRes = mockMvc.perform(get("/api/v1/students/me/classes?page=0&size=1")
                .header("Authorization", "Bearer " + studentToken))
            .andReturn().getResponse().getContentAsString();
        classId = objectMapper.readTree(myClassesRes).get("data").get("content").get(0).get("classId").asText();

        // Create an experiment for tests
        String experimentContent = """
            {
                "subjectId": "%s",
                "title": "Thí nghiệm kiểm thử %s",
                "description": "Mô phỏng phục vụ test case"
            }
            """.formatted(subjectId, UUID.randomUUID());
            
        String expRes = mockMvc.perform(post("/api/v1/experiments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(experimentContent))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        experimentId = objectMapper.readTree(expRes).get("data").get("experimentId").asText();
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
    @DisplayName("EXP-01: Admin tạo bài thí nghiệm thành công")
    void createExperiment_asAdmin_success() throws Exception {
        String experimentContent = """
            {
                "subjectId": "%s",
                "title": "Thí nghiệm con lắc lò xo",
                "description": "Mô phỏng dao động điều hòa"
            }
            """.formatted(subjectId);
            
        mockMvc.perform(post("/api/v1/experiments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(experimentContent))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Thí nghiệm con lắc lò xo"));
    }

    @Test
    @DisplayName("EXP-02: Giảng viên hoặc Admin giao bài thí nghiệm cho lớp thành công")
    void assignExperiment_asInstructorOrAdmin_success() throws Exception {
        String assignPayload = """
            {
                "classId": "%s",
                "dueDate": "%s",
                "instructionsOverride": "Nộp báo cáo trước hạn"
            }
            """.formatted(classId, Instant.now().plusSeconds(86400 * 7));

        mockMvc.perform(post("/api/v1/experiments/" + experimentId + "/assign")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.classId").value(classId));
    }

    @Test
    @DisplayName("EXP-03: Sinh viên không được phép giao bài thí nghiệm cho lớp (403 Forbidden)")
    void assignExperiment_asStudent_returns403() throws Exception {
        String assignPayload = """
            {
                "classId": "%s",
                "dueDate": "%s"
            }
            """.formatted(classId, Instant.now().plusSeconds(86400 * 7));

        mockMvc.perform(post("/api/v1/experiments/" + experimentId + "/assign")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXP-04: Sinh viên nộp bài thí nghiệm thành công khi lớp đã được giao bài")
    void submitExperiment_asStudent_success() throws Exception {
        // Giao bài trước
        String assignPayload = """
            {
                "classId": "%s",
                "dueDate": "%s"
            }
            """.formatted(classId, Instant.now().plusSeconds(86400 * 7));

        String assignRes = mockMvc.perform(post("/api/v1/experiments/" + experimentId + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String assignmentId = objectMapper.readTree(assignRes).get("data").get("assignmentId").asText();

        // Sinh viên nộp bài
        MockMultipartFile file = new MockMultipartFile("file", "baocao.pdf", MediaType.APPLICATION_PDF_VALUE, "noidungbaocao".getBytes());

        mockMvc.perform(multipart("/api/v1/experiments/assignments/" + assignmentId + "/submit")
                .file(file)
                .param("evidenceUrl", "https://storage.example.com/evidence1.png")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("EXP-05: Sinh viên nộp bài cho assignment không tồn tại hoặc chưa giao cho lớp phải bị từ chối 404")
    void submitExperiment_whenAssignmentNotFound_returns404() throws Exception {
        UUID fakeAssignmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/experiments/assignments/" + fakeAssignmentId + "/submit")
                .file(file)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EXP-06A: Chấm điểm theo rubric (scores) — chỉ Instructor/TA có quyền, Sinh viên bị từ chối 403")
    void gradeExperiment_asStudent_returns403() throws Exception {
        UUID fakeSubmissionId = UUID.randomUUID();
        String scorePayload = """
            {
                "rubricId": "%s",
                "score": 9.5,
                "feedback": "Làm bài rất tốt"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/experiments/submissions/" + fakeSubmissionId + "/scores")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scorePayload))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXP-06B: Giảng viên chấm điểm rubric thành công và cập nhật trạng thái GRADED trong CSDL")
    void gradeExperiment_asInstructor_success() throws Exception {
        ExperimentSubmission submission = experimentSubmissionRepository.save(ExperimentSubmission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build());
        UUID submissionId = submission.getSubmissionId();

        String scorePayload = """
            {
                "rubricId": "%s",
                "score": 8.5,
                "feedback": "Báo cáo thực hành tốt"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/experiments/submissions/" + submissionId + "/scores")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scorePayload))
            .andExpect(status().isOk());

        // Kiểm tra thực tế trong CSDL: trạng thái chuyển sang GRADED
        ExperimentSubmission updated = experimentSubmissionRepository.findById(submissionId).orElseThrow();
        Assertions.assertEquals(SubmissionStatus.GRADED, updated.getStatus());
    }

    @Test
    @DisplayName("EXP-07: Xác nhận kết quả cuối (confirmation) — lưu DB & từ chối sửa nếu đã confirmed")
    void confirmExperiment_whenAlreadyConfirmed_returns400() throws Exception {
        ExperimentSubmission submission = experimentSubmissionRepository.save(ExperimentSubmission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build());
        UUID submissionId = submission.getSubmissionId();

        String confirmPayload = """
            {
                "note": "Xác nhận điểm số chung cuộc"
            }
            """;

        // Lần 1: Xác nhận thành công (200 OK) -> Lưu DB status CONFIRMED và lưu experiment_confirmations
        mockMvc.perform(post("/api/v1/experiments/submissions/" + submissionId + "/confirmation")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isOk());

        // Kiểm tra thực tế trong CSDL:
        Assertions.assertTrue(experimentConfirmationRepository.existsBySubmissionId(submissionId));
        ExperimentSubmission confirmed = experimentSubmissionRepository.findById(submissionId).orElseThrow();
        Assertions.assertEquals(SubmissionStatus.CONFIRMED, confirmed.getStatus());

        // Lần 2: Cố xác nhận lại bài đã confirmed -> bị từ chối 400 Bad Request
        mockMvc.perform(post("/api/v1/experiments/submissions/" + submissionId + "/confirmation")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isBadRequest());

        // Lần 3: Cố chấm điểm lại bài đã confirmed -> cũng bị từ chối 400 Bad Request (bất biến)
        mockMvc.perform(post("/api/v1/experiments/submissions/" + submissionId + "/scores")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("EXP-08: Xác nhận kết quả cho submissionId không tồn tại trả về 404 Not Found")
    void confirmExperiment_whenNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/experiments/submissions/" + UUID.randomUUID() + "/confirmation")
                .header("Authorization", "Bearer " + instructorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"test\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EXP-09: [Concurrency] 2 luồng đồng thời gọi confirmation cho cùng 1 submission -> 1 thành công (200), 1 bị từ chối (400), không bao giờ lỗi 500")
    void confirmExperiment_concurrency_onlyOneSucceeds() throws Exception {
        ExperimentSubmission submission = experimentSubmissionRepository.save(ExperimentSubmission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build());
        UUID submissionId = submission.getSubmissionId();

        int threads = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger status200 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger status400 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger otherStatus = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int code = mockMvc.perform(post("/api/v1/experiments/submissions/" + submissionId + "/confirmation")
                            .header("Authorization", "Bearer " + instructorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":\"Concurrent confirm test\"}"))
                        .andReturn().getResponse().getStatus();
                    if (code == 200) status200.incrementAndGet();
                    else if (code == 400) status400.incrementAndGet();
                    else otherStatus.incrementAndGet();
                } catch (Exception ignored) {}
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Kiểm tra kết quả luồng:
        Assertions.assertEquals(1, status200.get(), "Chính xác 1 luồng được xác nhận thành công (200 OK)");
        Assertions.assertEquals(1, status400.get(), "Luồng thứ 2 phải nhận 400 Bad Request hợp lệ (chặn bởi App logic hoặc UNIQUE constraint)");
        Assertions.assertEquals(0, otherStatus.get(), "Tuyệt đối không được văng lỗi 500 Internal Server Error");

        // Kiểm tra trong CSDL: Chỉ có đúng 1 bản ghi xác nhận và status là CONFIRMED
        Assertions.assertTrue(experimentConfirmationRepository.existsBySubmissionId(submissionId));
        Assertions.assertEquals(SubmissionStatus.CONFIRMED, experimentSubmissionRepository.findById(submissionId).get().getStatus());
    }

    @Test
    @DisplayName("EXP-10: 04 Thí nghiệm ảo 3D Vật lý 1 chuẩn được seed tự động và có đầy đủ Rubric JSON, WebGL URL")
    void testSeededPhysics1Experiments_availableWithRubrics() throws Exception {
        // Tìm subject PHY101
        String subjectsRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=10")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode contentNode = objectMapper.readTree(subjectsRes).get("data").get("content");
        String phy101Id = null;
        for (com.fasterxml.jackson.databind.JsonNode s : contentNode) {
            if ("PHY101".equals(s.get("subjectCode").asText())) {
                phy101Id = s.get("subjectId").asText();
                break;
            }
        }
        Assertions.assertNotNull(phy101Id, "PHY101 phải tồn tại trong danh sách môn học");

        // Lấy danh sách bài thí nghiệm của PHY101
        String expListRes = mockMvc.perform(get("/api/v1/experiments?subjectId=" + phy101Id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode expList = objectMapper.readTree(expListRes).get("data");
        Assertions.assertTrue(expList.size() >= 4, "Phải có ít nhất 4 bài thí nghiệm ảo Vật lý 1");

        // Kiểm tra chi tiết 4 bài thí nghiệm
        List<String> titles = new ArrayList<>();
        List<String> simUrls = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode exp : expList) {
            titles.add(exp.get("title").asText());
            simUrls.add(exp.get("sceneAssetUrl").asText());
            Assertions.assertNotNull(exp.get("sceneAssetsJson"), "sceneAssetsJson chứa rubric và tài nguyên 3D không được rỗng");
            Assertions.assertTrue(exp.get("sceneAssetsJson").has("rubric"), "sceneAssetsJson phải chứa rubric đánh giá tiêu chuẩn");
        }

        Assertions.assertTrue(titles.stream().anyMatch(t -> t.contains("rơi tự do")), "Có bài thí nghiệm Rơi tự do");
        Assertions.assertTrue(titles.stream().anyMatch(t -> t.contains("con lắc đơn")), "Có bài thí nghiệm Con lắc đơn");
        Assertions.assertTrue(titles.stream().anyMatch(t -> t.contains("đĩa tròn") || t.contains("quán tính")), "Có bài thí nghiệm Mô-men quán tính đĩa tròn");
        Assertions.assertTrue(titles.stream().anyMatch(t -> t.contains("đệm không khí") || t.contains("va chạm")), "Có bài thí nghiệm Va chạm đệm không khí");

        Assertions.assertTrue(simUrls.stream().anyMatch(u -> u.contains("free-fall-3d")));
        Assertions.assertTrue(simUrls.stream().anyMatch(u -> u.contains("simple-pendulum-3d")));
        Assertions.assertTrue(simUrls.stream().anyMatch(u -> u.contains("rotational-inertia-3d")));
        Assertions.assertTrue(simUrls.stream().anyMatch(u -> u.contains("air-track-collision-3d")));
    }

    private String createSubmissionForSeededExperiment() throws Exception {
        com.vatly1.example.entity.Experiment exp = experimentRepository.save(com.vatly1.example.entity.Experiment.builder()
                .subjectId(UUID.fromString(subjectId))
                .title("Thí nghiệm test rubric " + UUID.randomUUID())
                .description("Mô tả test rubric")
                .orderIndex(99)
                .build());

        experimentRubricRepository.save(com.vatly1.example.entity.ExperimentRubric.builder()
                .experimentId(exp.getExperimentId())
                .criteriaName("Thao tác cài đặt & thu thập số liệu")
                .maxScore(new java.math.BigDecimal("3.00"))
                .description("Lắp ráp đúng mô hình")
                .build());

        experimentRubricRepository.save(com.vatly1.example.entity.ExperimentRubric.builder()
                .experimentId(exp.getExperimentId())
                .criteriaName("Xử lý số liệu, vẽ đồ thị")
                .maxScore(new java.math.BigDecimal("3.00"))
                .description("Tính sai số và vẽ đồ thị")
                .build());

        experimentRubricRepository.save(com.vatly1.example.entity.ExperimentRubric.builder()
                .experimentId(exp.getExperimentId())
                .criteriaName("Phân tích kết quả")
                .maxScore(new java.math.BigDecimal("4.00"))
                .description("Phân tích nguyên nhân sai số")
                .build());

        com.vatly1.example.entity.ExperimentAssignment assignment = experimentAssignmentRepository.save(com.vatly1.example.entity.ExperimentAssignment.builder()
                .experimentId(exp.getExperimentId())
                .classId(UUID.fromString(classId))
                .assignedBy(userRepository.findByUsername("admin").getUserId())
                .dueDate(Instant.now().plusSeconds(86400 * 7))
                .createdAt(Instant.now())
                .build());

        ExperimentSubmission submission = experimentSubmissionRepository.save(ExperimentSubmission.builder()
                .assignmentId(assignment.getAssignmentId())
                .studentId(userRepository.findByUsername("sv_an").getUserId())
                .evidenceUrl("https://storage.example.com/test.png")
                .status(SubmissionStatus.PENDING)
                .submittedAt(Instant.now())
                .build());

        return submission.getSubmissionId().toString();
    }

    @Test
    @DisplayName("EXP-11: TA lấy danh sách Rubric theo submissionId thành công (GET /rubrics và GET /scores)")
    void getSubmissionRubrics_asTA_success() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // TA gọi GET /api/v1/experiments/submissions/{submissionId}/rubrics
        String rubricsRes = mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[0].rubricId").exists())
                .andExpect(jsonPath("$.data[0].criteriaName").exists())
                .andExpect(jsonPath("$.data[0].maxScore").exists())
                .andExpect(jsonPath("$.data[0].isGraded").value(false))
                .andReturn().getResponse().getContentAsString();

        // TA gọi alias GET /api/v1/experiments/submissions/{submissionId}/scores
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/scores")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("EXP-12: TA lấy Rubric cụ thể theo query param (?rubricId=) và path variable (/{rubricId})")
    void getSubmissionRubric_withRubricId_asTA_success() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // Lấy danh sách rubric trước
        String rubricsRes = mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(rubricsRes).get("data");
        String targetRubricId = list.get(0).get("rubricId").asText();

        // 1. Thử lọc bằng query param ?rubricId=...
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics?rubricId=" + targetRubricId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].rubricId").value(targetRubricId));

        // 2. Thử gọi trực tiếp bằng path variable /{rubricId}
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics/" + targetRubricId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rubricId").value(targetRubricId))
                .andExpect(jsonPath("$.data.criteriaName").exists());

        // 3. Thử path variable alias /scores/{rubricId}
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/scores/" + targetRubricId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rubricId").value(targetRubricId));
    }

    @Test
    @DisplayName("EXP-13: TA chấm điểm tiêu chí Rubric (POST /scores) và đọc lại điểm số (GET /rubrics/{rubricId})")
    void gradeSubmission_asTA_andVerifyRetrieval_success() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // Lấy rubricId
        String rubricsRes = mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(rubricsRes).get("data");
        String targetRubricId = list.get(0).get("rubricId").asText();

        // TA chấm điểm cho tiêu chí này: 2.5 điểm, feedback "Thao tác đo rất chuẩn"
        String gradeBody = """
            {
                "rubricId": "%s",
                "score": 2.5,
                "feedback": "Thao tác đo rất chuẩn",
                "comment": "Thao tác đo rất chuẩn"
            }
            """.formatted(targetRubricId);

        mockMvc.perform(post("/api/v1/experiments/submissions/" + subId + "/scores")
                        .header("Authorization", "Bearer " + taToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Score saved successfully"));

        // TA đọc lại rubric vừa chấm
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics/" + targetRubricId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rubricId").value(targetRubricId))
                .andExpect(jsonPath("$.data.score").value(2.5))
                .andExpect(jsonPath("$.data.feedback").value("Thao tác đo rất chuẩn"))
                .andExpect(jsonPath("$.data.isGraded").value(true));

        // Kiểm tra tổng hợp rubric summary
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubric-summary")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(2.5))
                .andExpect(jsonPath("$.data.status").value("GRADED"));
    }

    @Test
    @DisplayName("EXP-14: Sinh viên chính chủ xem được rubric bài nộp, sinh viên khác bị từ chối 403 Forbidden")
    void getSubmissionRubrics_studentPermissions_verified() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // Sinh viên chính chủ (sv_an) xem bài nộp của mình -> 200 OK
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Sinh viên khác (sv_binh) cố xem bài nộp của sv_an -> 403 Forbidden
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + studentBinhToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXP-15: Chấm điểm vượt quá max_score bị từ chối 400, tra cứu submission không tồn tại trả về 404")
    void rubricValidation_errorsHandled() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // Lấy rubricId
        String rubricsRes = mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(rubricsRes).get("data");
        String targetRubricId = list.get(0).get("rubricId").asText();

        // Chấm điểm vượt quá max_score (ví dụ 999.0)
        String invalidScoreBody = """
            {
                "rubricId": "%s",
                "score": 999.0
            }
            """.formatted(targetRubricId);

        mockMvc.perform(post("/api/v1/experiments/submissions/" + subId + "/scores")
                        .header("Authorization", "Bearer " + taToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidScoreBody))
                .andExpect(status().isBadRequest());

        // Tra cứu submissionId không tồn tại -> 404
        mockMvc.perform(get("/api/v1/experiments/submissions/" + UUID.randomUUID() + "/rubrics")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isNotFound());

        // Tra cứu rubricId không tồn tại -> 404
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId + "/rubrics/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EXP-16: TA lấy danh sách bài nộp GET /api/v1/experiments/submissions thành công")
    void getSubmissions_asTA_success() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        String res = mockMvc.perform(get("/api/v1/experiments/submissions")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode data = objectMapper.readTree(res).get("data");
        Assertions.assertTrue(data.size() > 0);

        // Tìm bài nộp vừa tạo
        boolean found = false;
        for (com.fasterxml.jackson.databind.JsonNode item : data) {
            if (subId.equals(item.get("submissionId").asText())) {
                found = true;
                Assertions.assertEquals("sv_an", item.get("studentUsername").asText());
                Assertions.assertNotNull(item.get("experimentTitle"));
                Assertions.assertEquals("PENDING", item.get("status").asText());
                Assertions.assertEquals(3, item.get("totalRubricCount").asInt());
                Assertions.assertEquals(10.0, item.get("totalMaxScore").asDouble(), 0.01);
                break;
            }
        }
        Assertions.assertTrue(found, "Bài nộp vừa tạo phải có trong danh sách bài nộp của TA");
    }

    @Test
    @DisplayName("EXP-17: TA lọc bài nộp theo assignmentId, classId, status hoặc gọi /assignments/{id}/submissions")
    void getSubmissions_withFilters_andByAssignment_success() throws Exception {
        String subId = createSubmissionForSeededExperiment();
        ExperimentSubmission sub = experimentSubmissionRepository.findById(UUID.fromString(subId)).orElseThrow();
        UUID assignmentId = sub.getAssignmentId();

        // 1. Lọc qua query param ?assignmentId
        mockMvc.perform(get("/api/v1/experiments/submissions?assignmentId=" + assignmentId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].submissionId").value(subId));

        // 2. Gọi endpoint chuyên biệt /assignments/{assignmentId}/submissions
        mockMvc.perform(get("/api/v1/experiments/assignments/" + assignmentId + "/submissions")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].submissionId").value(subId));

        // 3. Lọc theo status=PENDING
        mockMvc.perform(get("/api/v1/experiments/submissions?status=PENDING")
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("EXP-18: TA và Sinh viên xem chi tiết bài nộp GET /submissions/{id}, sinh viên khác bị 403")
    void getSubmissionById_success_andRBAC() throws Exception {
        String subId = createSubmissionForSeededExperiment();

        // 1. TA xem chi tiết bài nộp -> 200 OK kèm toàn bộ rubrics
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId)
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(subId))
                .andExpect(jsonPath("$.data.studentUsername").value("sv_an"))
                .andExpect(jsonPath("$.data.rubrics").isArray())
                .andExpect(jsonPath("$.data.rubrics.length()").value(3));

        // 2. Sinh viên chủ bài nộp (sv_an) xem chi tiết -> 200 OK
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(subId));

        // 3. Sinh viên khác (sv_binh) cố xem bài nộp của sv_an -> 403 Forbidden
        mockMvc.perform(get("/api/v1/experiments/submissions/" + subId)
                        .header("Authorization", "Bearer " + studentBinhToken))
                .andExpect(status().isForbidden());

        // 4. Tra cứu submissionId không tồn tại -> 404 Not Found
        mockMvc.perform(get("/api/v1/experiments/submissions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + taToken))
                .andExpect(status().isNotFound());
    }
}