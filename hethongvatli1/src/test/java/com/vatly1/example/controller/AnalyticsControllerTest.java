package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.dto.request.TriggerAggregationRequestDTO;
import com.vatly1.example.entity.AiTopicGapStat;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.MaterialEffectivenessStat;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionStat;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.TopicDifficultyStat;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.repository.AiTopicGapStatRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.MaterialEffectivenessStatRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionStatRepository;
import com.vatly1.example.repository.TopicDifficultyStatRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IAnalyticsAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamRepository;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class, properties = "scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private TopicDifficultyStatRepository topicDifficultyStatRepository;

    @Autowired
    private QuestionStatRepository questionStatRepository;

    @Autowired
    private AiTopicGapStatRepository aiTopicGapStatRepository;

    @Autowired
    private MaterialEffectivenessStatRepository materialEffectivenessStatRepository;

    @Autowired
    private IAnalyticsAggregationService aggregationService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IExamAnswerRepository examAnswerRepository;

    private String adminToken;
    private String instructorToken;
    private String otherInstructorToken;
    private String studentToken;

    private Class targetClass;
    private Subject targetSubject;
    private Topic targetTopic;
    private QuestionBank targetQuestion;
    private LearningMaterial targetMaterial;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        otherInstructorToken = signin("gv_tran", "gv_tran123456");
        studentToken = signin("sv_an", "sv_an123456");

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()))
                .findFirst()
                .orElseThrow();

        targetSubject = subjectRepository.findAll().stream()
                .filter(s -> "PHY101".equals(s.getSubjectCode()))
                .findFirst()
                .orElseThrow();

        // Ensure at least one Topic exists
        targetTopic = topicRepository.findAll().stream()
                .filter(t -> Objects.equals(t.getSubjectId(), targetSubject.getSubjectId()))
                .findFirst()
                .orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(targetSubject.getSubjectId())
                        .topicName("Cơ học chất điểm")
                        .orderIndex(1)
                        .description("Chương 1")
                        .build()));

        // Ensure at least one Question exists
        targetQuestion = questionBankRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> questionBankRepository.save(QuestionBank.builder()
                        .subjectId(targetSubject.getSubjectId())
                        .topicId(targetTopic.getTopicId())
                        .questionType(QuestionType.MCQ_SINGLE)
                        .content("Định luật I Newton đề cập đến tính chất gì của vật?")
                        .difficultyLevel(DifficultyLevel.EASY)
                        .cognitiveLevel("REMEMBER")
                        .createdBy(gvNguyen.getUserId())
                        .approvalStatus(ApprovalStatus.APPROVED)
                        .build()));

        // Ensure at least one Material exists
        targetMaterial = learningMaterialRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> learningMaterialRepository.save(LearningMaterial.builder()
                        .topicId(targetTopic.getTopicId())
                        .title("Bài giảng Cơ học chương 1")
                        .type(MaterialType.PDF)
                        .version(1)
                        .approvalStatus(ApprovalStatus.APPROVED)
                        .createdBy(gvNguyen.getUserId())
                        .build()));

        topicDifficultyStatRepository.deleteAll();
        questionStatRepository.deleteAll();
        aiTopicGapStatRepository.deleteAll();
        materialEffectivenessStatRepository.deleteAll();
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("AN-01: GV phụ trách GET /topic-difficulty?classId=X -> 200, trả danh sách độ khó chủ đề")
    void testAN01_InstructorGetTopicDifficulty() throws Exception {
        topicDifficultyStatRepository.deleteByPeriod("2024-09");
        topicDifficultyStatRepository.save(TopicDifficultyStat.builder()
                .classId(targetClass.getClassId())
                .subjectId(targetSubject.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .avgScore(BigDecimal.valueOf(78.50))
                .errorRate(BigDecimal.valueOf(0.22))
                .period("2024-09")
                .generatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .param("classId", targetClass.getClassId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].topicName").value("Cơ học chất điểm"))
                .andExpect(jsonPath("$.data[0].avgScore").value(78.50))
                .andExpect(jsonPath("$.data[0].errorRate").value(0.22));
    }

    @Test
    @DisplayName("AN-02: GV gọi GET /topic-difficulty cho lớp chưa có số liệu -> 200, trả mảng rỗng")
    void testAN02_InstructorGetTopicDifficulty_Empty() throws Exception {
        topicDifficultyStatRepository.deleteAll();

        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .param("classId", targetClass.getClassId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("AN-03: Sinh viên gọi GET /topic-difficulty -> 403 Forbidden")
    void testAN03_StudentGetTopicDifficulty_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AN-04: GV gọi GET /question-quality -> 200, tính đúng qualityLabel (EXCELLENT)")
    void testAN04_InstructorGetQuestionQuality_Success() throws Exception {
        questionStatRepository.save(QuestionStat.builder()
                .questionId(targetQuestion.getQuestionId())
                .timesUsed(25)
                .correctRate(BigDecimal.valueOf(0.6800))
                .discriminationIndex(BigDecimal.valueOf(0.4200))
                .updatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/question-quality")
                        .param("subjectId", targetSubject.getSubjectId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].qualityLabel").value("EXCELLENT"))
                .andExpect(jsonPath("$.data[0].timesUsed").value(25));
    }

    @Test
    @DisplayName("AN-05: GV gọi GET /question-quality với minUsed=20 -> lọc bỏ câu hỏi ít dùng")
    void testAN05_InstructorGetQuestionQuality_MinUsedFilter() throws Exception {
        questionStatRepository.save(QuestionStat.builder()
                .questionId(targetQuestion.getQuestionId())
                .timesUsed(5)
                .correctRate(BigDecimal.valueOf(0.5000))
                .discriminationIndex(BigDecimal.valueOf(0.2000))
                .updatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/question-quality")
                        .param("minUsed", "10")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("AN-06: GV gọi GET /ai-gaps -> 200, trả thống kê từ chối AI theo chủ đề")
    void testAN06_InstructorGetAiGaps_Success() throws Exception {
        aiTopicGapStatRepository.deleteByPeriod("2024-09");
        aiTopicGapStatRepository.save(AiTopicGapStat.builder()
                .subjectId(targetSubject.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .refusalCount(8)
                .frequentQuerySample("Chuyển động tương đối trong hệ quy chiếu phi quán tính")
                .period("2024-09")
                .generatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/ai-gaps")
                        .param("subjectId", targetSubject.getSubjectId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].refusalCount").value(8))
                .andExpect(jsonPath("$.data[0].topicName").value("Cơ học chất điểm"));
    }

    @Test
    @DisplayName("AN-07: GV gọi GET /material-effectiveness -> 200, trả thống kê lượt xem học liệu")
    void testAN07_InstructorGetMaterialEffectiveness_Success() throws Exception {
        materialEffectivenessStatRepository.deleteByPeriod("2024-09");
        materialEffectivenessStatRepository.save(MaterialEffectivenessStat.builder()
                .materialId(targetMaterial.getMaterialId())
                .period("2024-09")
                .viewCount(45)
                .avgTimeSpentSeconds(BigDecimal.valueOf(180.5))
                .correlatedScoreImprovement(BigDecimal.valueOf(1.25))
                .build());

        mockMvc.perform(get("/api/v1/analytics/material-effectiveness")
                        .param("subjectId", targetSubject.getSubjectId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].title").value("Bài giảng Cơ học chương 1"))
                .andExpect(jsonPath("$.data[0].viewCount").value(45));
    }

    @Test
    @DisplayName("AN-08: Admin gọi POST /trigger -> 204 No Content, kích hoạt tổng hợp thủ công")
    void testAN08_AdminTriggerAggregation_Success() throws Exception {
        TriggerAggregationRequestDTO body = TriggerAggregationRequestDTO.builder()
                .period("2024-09")
                .build();

        mockMvc.perform(post("/api/v1/analytics/trigger")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AN-09: Giảng viên hoặc Sinh viên gọi POST /trigger -> 403 Forbidden (Chỉ Admin)")
    void testAN09_NonAdminTriggerAggregation_Forbidden() throws Exception {
        TriggerAggregationRequestDTO body = TriggerAggregationRequestDTO.builder()
                .period("2024-09")
                .build();

        mockMvc.perform(post("/api/v1/analytics/trigger")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/analytics/trigger")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AN-10: GET /topic-difficulty?period=2024-09 -> lọc chính xác theo chu kỳ period")
    void testAN10_TopicDifficulty_PeriodFilter() throws Exception {
        topicDifficultyStatRepository.deleteByPeriod("2024-09");
        topicDifficultyStatRepository.deleteByPeriod("2024-10");

        topicDifficultyStatRepository.save(TopicDifficultyStat.builder()
                .classId(targetClass.getClassId())
                .subjectId(targetSubject.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .avgScore(BigDecimal.valueOf(70.0))
                .errorRate(BigDecimal.valueOf(0.30))
                .period("2024-09")
                .generatedAt(Instant.now())
                .build());

        topicDifficultyStatRepository.save(TopicDifficultyStat.builder()
                .classId(targetClass.getClassId())
                .subjectId(targetSubject.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .avgScore(BigDecimal.valueOf(85.0))
                .errorRate(BigDecimal.valueOf(0.15))
                .period("2024-10")
                .generatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .param("classId", targetClass.getClassId().toString())
                        .param("period", "2024-09")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].period").value("2024-09"))
                .andExpect(jsonPath("$.data[0].avgScore").value(70.0));
    }

    @Test
    @DisplayName("AN-11: GET /question-quality -> Kiểm tra chỉ số phân biệt DI thuộc khoảng chuẩn [-1.0, 1.0]")
    void testAN11_QuestionQuality_DiscriminationIndexRange() throws Exception {
        questionStatRepository.save(QuestionStat.builder()
                .questionId(targetQuestion.getQuestionId())
                .timesUsed(50)
                .correctRate(BigDecimal.valueOf(0.55))
                .discriminationIndex(BigDecimal.valueOf(0.25))
                .updatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/analytics/question-quality")
                        .param("subjectId", targetSubject.getSubjectId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].discriminationIndex", greaterThanOrEqualTo(-1.0)))
                .andExpect(jsonPath("$.data[0].discriminationIndex", lessThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.data[0].qualityLabel").value("GOOD"));
    }

    @Test
    @DisplayName("AN-12: IDOR: Giảng viên B (gv_tran) truy vấn lớp của Giảng viên A -> 403 Forbidden")
    void testAN12_IDOR_OtherInstructorAccessClass_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .param("classId", targetClass.getClassId().toString())
                        .header("Authorization", "Bearer " + otherInstructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AN-13: Admin truy vấn độ khó của bất kỳ lớp học nào -> 200 OK (Admin Bypass IDOR)")
    void testAN13_AdminAccessClassTopicDifficulty_BypassIDOR() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topic-difficulty")
                        .param("classId", targetClass.getClassId().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("AN-14: Service Aggregation: Thực thi tổng hợp dữ liệu mẫu không phát sinh biệt lệ")
    void testAN14_FullAggregationService_ExecutesWithoutError() {
        assertDoesNotThrow(() -> aggregationService.triggerFullAggregation("2024-09"));
    }

    @Test
    @DisplayName("AN-15: Tính toán chính xác giá trị Chỉ số phân biệt (DI) so với tính tay CTT trên 10 bài thi mẫu")
    void testAN15_DiscriminationIndexExactCalculation() throws Exception {
        Exam diExam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Kiểm tra phân hóa học lực")
                .examType(ExamType.QUIZ)
                .durationMinutes(45)
                .startTime(Instant.parse("2024-09-01T00:00:00Z"))
                .endTime(Instant.parse("2024-09-30T23:59:59Z"))
                .createdBy(targetClass.getInstructorId())
                .createdAt(Instant.now())
                .build());

        double[] scores = {100.0, 90.0, 80.0, 70.0, 60.0, 50.0, 40.0, 30.0, 20.0, 10.0};
        boolean[] isCorrect = {true, true, true, false, false, false, false, false, false, false};

        for (int i = 0; i < 10; i++) {
            UUID sId = UUID.randomUUID();
            ExamAttempt att = examAttemptRepository.save(ExamAttempt.builder()
                    .examId(diExam.getExamId())
                    .studentId(sId)
                    .startedAt(Instant.parse("2024-09-15T08:00:00Z"))
                    .submittedAt(Instant.parse("2024-09-15T08:45:00Z"))
                    .status(AttemptStatus.SUBMITTED)
                    .totalScore(BigDecimal.valueOf(scores[i]))
                    .build());

            examAnswerRepository.save(ExamAnswer.builder()
                    .attemptId(att.getAttemptId())
                    .questionId(targetQuestion.getQuestionId())
                    .isCorrect(isCorrect[i])
                    .score(isCorrect[i] ? BigDecimal.ONE : BigDecimal.ZERO)
                    .build());
        }

        aggregationService.aggregateQuestionStats("2024-09");

        QuestionStat stat = questionStatRepository.findById(targetQuestion.getQuestionId()).orElseThrow();
        assertEquals(10, stat.getTimesUsed());
        assertEquals(0, stat.getDiscriminationIndex().compareTo(new BigDecimal("1.0000")));

        mockMvc.perform(get("/api/v1/analytics/question-quality")
                        .param("topicId", targetTopic.getTopicId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].discriminationIndex").value(1.0))
                .andExpect(jsonPath("$.data[0].qualityLabel").value("EXCELLENT"));
    }

    @Test
    @DisplayName("AN-16: Xử lý an toàn lớp học có sĩ số nhỏ (< 4 thí sinh), DI trả về null, tránh chia cho 0 và NaN")
    void testAN16_SmallClassSize_DiscriminationIndexReturnsNull() throws Exception {
        QuestionBank smallQuestion = questionBankRepository.save(QuestionBank.builder()
                .subjectId(targetSubject.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Câu hỏi kiểm tra lớp sĩ số nhỏ")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .cognitiveLevel("UNDERSTAND")
                .createdBy(targetClass.getInstructorId())
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());

        Exam smallExam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Đề kiểm tra lớp thực hành nhỏ")
                .examType(ExamType.QUIZ)
                .durationMinutes(30)
                .startTime(Instant.parse("2024-09-01T00:00:00Z"))
                .endTime(Instant.parse("2024-09-30T23:59:59Z"))
                .createdBy(targetClass.getInstructorId())
                .createdAt(Instant.now())
                .build());

        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        ExamAttempt att1 = examAttemptRepository.save(ExamAttempt.builder()
                .examId(smallExam.getExamId())
                .studentId(s1)
                .submittedAt(Instant.parse("2024-09-15T09:00:00Z"))
                .status(AttemptStatus.SUBMITTED)
                .totalScore(BigDecimal.valueOf(90.0))
                .build());

        examAnswerRepository.save(ExamAnswer.builder()
                .attemptId(att1.getAttemptId())
                .questionId(smallQuestion.getQuestionId())
                .isCorrect(true)
                .score(BigDecimal.ONE)
                .build());

        ExamAttempt att2 = examAttemptRepository.save(ExamAttempt.builder()
                .examId(smallExam.getExamId())
                .studentId(s2)
                .submittedAt(Instant.parse("2024-09-15T09:00:00Z"))
                .status(AttemptStatus.SUBMITTED)
                .totalScore(BigDecimal.valueOf(40.0))
                .build());

        examAnswerRepository.save(ExamAnswer.builder()
                .attemptId(att2.getAttemptId())
                .questionId(smallQuestion.getQuestionId())
                .isCorrect(false)
                .score(BigDecimal.ZERO)
                .build());

        aggregationService.aggregateQuestionStats("2024-09");

        QuestionStat stat = questionStatRepository.findById(smallQuestion.getQuestionId()).orElseThrow();
        assertEquals(2, stat.getTimesUsed());
        assertEquals(0, stat.getCorrectRate().compareTo(new BigDecimal("0.5000")));
        assertNull(stat.getDiscriminationIndex());

        mockMvc.perform(get("/api/v1/analytics/question-quality")
                        .param("topicId", targetTopic.getTopicId().toString())
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.questionId == '" + smallQuestion.getQuestionId() + "')].qualityLabel").value("UNEVALUATED"));
    }

    @Test
    @DisplayName("AN-17: Kiểm chứng tính Idempotent thực tế: Chạy tổng hợp 2 lần liên tiếp không nhân đôi dữ liệu")
    void testAN17_IdempotentAggregation_MultipleRunsDoNotDuplicateData() throws Exception {
        aggregationService.triggerFullAggregation("2024-09");
        long diffCount1 = topicDifficultyStatRepository.count();
        long questCount1 = questionStatRepository.count();
        long aiCount1 = aiTopicGapStatRepository.count();
        long matCount1 = materialEffectivenessStatRepository.count();

        aggregationService.triggerFullAggregation("2024-09");
        long diffCount2 = topicDifficultyStatRepository.count();
        long questCount2 = questionStatRepository.count();
        long aiCount2 = aiTopicGapStatRepository.count();
        long matCount2 = materialEffectivenessStatRepository.count();

        assertEquals(diffCount1, diffCount2, "Số lượng TopicDifficultyStat phải giữ nguyên sau nhiều lần chạy");
        assertEquals(questCount1, questCount2, "Số lượng QuestionStat phải giữ nguyên sau nhiều lần chạy");
        assertEquals(aiCount1, aiCount2, "Số lượng AiTopicGapStat phải giữ nguyên sau nhiều lần chạy");
        assertEquals(matCount1, matCount2, "Số lượng MaterialEffectivenessStat phải giữ nguyên sau nhiều lần chạy");

        TriggerAggregationRequestDTO body = TriggerAggregationRequestDTO.builder()
                .period("2024-09")
                .build();

        mockMvc.perform(post("/api/v1/analytics/trigger")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        assertEquals(diffCount1, topicDifficultyStatRepository.count());
        assertEquals(questCount1, questionStatRepository.count());
        assertEquals(aiCount1, aiTopicGapStatRepository.count());
        assertEquals(matCount1, materialEffectivenessStatRepository.count());
    }
}
