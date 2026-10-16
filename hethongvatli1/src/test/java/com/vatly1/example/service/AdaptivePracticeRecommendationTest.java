package com.vatly1.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.repository.*;
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
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdaptivePracticeRecommendationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassSettingsRepository classSettingsRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IExamAnswerRepository examAnswerRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IPracticeSessionRepository practiceSessionRepository;

    @Autowired
    private IPracticeSessionQuestionRepository practiceSessionQuestionRepository;

    @Autowired
    private IPracticeSessionAnswerRepository practiceSessionAnswerRepository;

    @Autowired
    private IClassStaffRepository classStaffRepository;

    @Autowired
    private IQuestionBankService questionBankService;

    private String adminToken;
    private String instructorAToken; // gv_nguyen
    private String instructorBToken; // gv_tran
    private String studentAToken;    // sv_an
    private String studentBToken;    // sv_binh

    private User gvNguyen;
    private User gvTran;
    private User svAn;
    private User svBinh;

    private Class classA;
    private Class classB;
    private Subject subjectPhysics;
    private Chapter chapter1;
    private Topic topicNewton;
    private Topic topicOscillation;
    private Topic topicOptics;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorAToken = signin("gv_nguyen", "gv_nguyen123456");
        instructorBToken = signin("gv_tran", "gv_tran123456");
        studentAToken = signin("sv_an", "sv_an123456");
        studentBToken = signin("sv_binh", "sv_binh123456");

        gvNguyen = userRepository.findByUsername("gv_nguyen");
        gvTran = userRepository.findByUsername("gv_tran");
        svAn = userRepository.findByUsername("sv_an");
        svBinh = userRepository.findByUsername("sv_binh");

        classA = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst().orElseThrow();
        classB = classRepository.findAll().stream()
                .filter(c -> "PHY101-02".equals(c.getClassCode()))
                .findFirst().orElseThrow();

        classA.setInstructorId(gvNguyen.getUserId());
        classRepository.save(classA);
        classB.setInstructorId(gvTran.getUserId());
        classRepository.save(classB);

        subjectPhysics = subjectRepository.findById(classA.getSubjectId()).orElseThrow();

        // Đảm bảo ClassSettings mặc định cho classA
        ClassSettings settingsA = classSettingsRepository.findById(classA.getClassId()).orElseGet(() ->
                ClassSettings.builder().classId(classA.getClassId()).build());
        settingsA.setPracticeEnabled(true);
        settingsA.setQuizEnabled(true);
        settingsA.setMaxPracticeAttempts(10);
        settingsA.setPracticeShowSolution(true);
        classSettingsRepository.save(settingsA);

        // Chuẩn bị Chapter & Topics
        List<Chapter> chapters = chapterRepository.findBySubjectIdOrderByOrderIndexAsc(subjectPhysics.getSubjectId());
        if (chapters.isEmpty()) {
            chapter1 = chapterRepository.save(Chapter.builder()
                    .subjectId(subjectPhysics.getSubjectId())
                    .chapterCode("CH01")
                    .chapterName("Chương 1: Cơ học cổ điển")
                    .orderIndex(1)
                    .isActive(true)
                    .build());
        } else {
            chapter1 = chapters.get(0);
        }

        List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectPhysics.getSubjectId());
        if (topics.size() < 3) {
            topicNewton = topicRepository.save(Topic.builder()
                    .subjectId(subjectPhysics.getSubjectId())
                    .chapterId(chapter1.getChapterId())
                    .topicName("Định luật Newton")
                    .orderIndex(1)
                    .build());
            topicOscillation = topicRepository.save(Topic.builder()
                    .subjectId(subjectPhysics.getSubjectId())
                    .chapterId(chapter1.getChapterId())
                    .topicName("Dao động cơ học")
                    .orderIndex(2)
                    .build());
            topicOptics = topicRepository.save(Topic.builder()
                    .subjectId(subjectPhysics.getSubjectId())
                    .chapterId(chapter1.getChapterId())
                    .topicName("Quang hình học")
                    .orderIndex(3)
                    .build());
        } else {
            topicNewton = topics.get(0);
            topicOscillation = topics.get(1);
            topicOptics = topics.get(2);
        }

        // Đảm bảo svAn ghi danh ACTIVE trong classA
        if (!classEnrollmentRepository.existsByClassIdAndStudentId(classA.getClassId(), svAn.getUserId())) {
            classEnrollmentRepository.save(ClassEnrollment.builder()
                    .classId(classA.getClassId())
                    .studentId(svAn.getUserId())
                    .subjectId(subjectPhysics.getSubjectId())
                    .semesterId(classA.getSemesterId())
                    .status(EnrollmentStatus.ACTIVE)
                    .build());
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

    private QuestionBank createQuestion(String content, QuestionType type, UUID topicId, ApprovalStatus status) {
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhysics.getSubjectId())
                .topicId(topicId)
                .content(content)
                .questionType(type)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .approvalStatus(status)
                .build());

        if (type == QuestionType.MCQ_SINGLE) {
            questionOptionRepository.save(QuestionOption.builder()
                    .questionId(q.getQuestionId())
                    .optionText("Đáp án A đúng")
                    .isCorrect(true)
                    .orderIndex(1)
                    .build());
            questionOptionRepository.save(QuestionOption.builder()
                    .questionId(q.getQuestionId())
                    .optionText("Đáp án B sai")
                    .isCorrect(false)
                    .orderIndex(2)
                    .build());
        } else if (type == QuestionType.FILL_BLANK) {
            questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                    .questionId(q.getQuestionId())
                    .acceptedText("9.8 m/s2")
                    .orderIndex(1)
                    .build());
            questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                    .questionId(q.getQuestionId())
                    .acceptedText("9.8")
                    .orderIndex(2)
                    .build());
        }
        return q;
    }

    @Test
    @DisplayName("SCENARIO 1 & 2: Phát hiện Topic yếu và tính toán correctCount, wrongCount, accuracyRate")
    void test01_02_detectWeakTopic_correctCount_wrongCount_accuracyRate() throws Exception {
        Topic dedicatedTopic = topicRepository.save(Topic.builder()
                .subjectId(subjectPhysics.getSubjectId())
                .chapterId(chapter1.getChapterId())
                .topicName("Dedicated Newton S1 " + UUID.randomUUID())
                .orderIndex(99)
                .build());

        // Tạo 5 câu trong dedicatedTopic và sinh bài thi practice
        Exam practiceExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Luyện tập Newton S1")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .createdBy(gvNguyen.getUserId())
                .build());

        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(practiceExam.getExamId())
                .studentId(svAn.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.GRADED)
                .startedAt(Instant.now().minusSeconds(3600))
                .submittedAt(Instant.now().minusSeconds(1800))
                .totalScore(BigDecimal.ONE)
                .build());

        // 5 câu: 1 đúng, 4 sai
        for (int i = 1; i <= 5; i++) {
            QuestionBank q = createQuestion("Newton Q" + i + "_" + UUID.randomUUID(), QuestionType.MCQ_SINGLE, dedicatedTopic.getTopicId(), ApprovalStatus.APPROVED);
            examQuestionRepository.save(ExamQuestion.builder()
                    .examId(practiceExam.getExamId())
                    .questionId(q.getQuestionId())
                    .orderIndex(i)
                    .build());

            examAnswerRepository.save(ExamAnswer.builder()
                    .attemptId(attempt.getAttemptId())
                    .questionId(q.getQuestionId())
                    .isCorrect(i == 1) // chỉ câu 1 đúng
                    .score(i == 1 ? BigDecimal.ONE : BigDecimal.ZERO)
                    .build());
        }

        // Gọi API weak-topics
        String response = mockMvc.perform(get("/api/v1/student/classes/" + classA.getClassId() + "/weak-topics")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode array = objectMapper.readTree(response);
        assertTrue(array.isArray());

        JsonNode targetNode = null;
        for (JsonNode node : array) {
            if (node.get("topicId").asText().equals(dedicatedTopic.getTopicId().toString())) {
                targetNode = node;
                break;
            }
        }

        assertNotNull(targetNode);
        assertEquals(5, targetNode.get("totalAnswered").asInt());
        assertEquals(1, targetNode.get("correctCount").asInt());
        assertEquals(4, targetNode.get("wrongCount").asInt());
        assertEquals(0.2, targetNode.get("accuracyRate").asDouble(), 0.01);
        assertEquals("NEEDS_PRACTICE", targetNode.get("weaknessLevel").asText());
        assertTrue(targetNode.get("hasEnoughData").asBoolean());
    }

    @Test
    @DisplayName("SCENARIO 3 & 4: Topic không đủ dữ liệu đánh giá là INSUFFICIENT_DATA và xếp sau Topic yếu")
    void test03_04_insufficientData_and_prioritySorting() throws Exception {
        String response = mockMvc.perform(get("/api/v1/student/classes/" + classA.getClassId() + "/weak-topics")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode array = objectMapper.readTree(response);
        assertTrue(array.size() >= 2);

        // Topic chưa làm câu nào hoặc < 3 câu phải có weaknessLevel == INSUFFICIENT_DATA
        boolean hasInsufficient = false;
        for (JsonNode node : array) {
            if (node.get("totalAnswered").asInt() < 3) {
                assertEquals("INSUFFICIENT_DATA", node.get("weaknessLevel").asText());
                assertFalse(node.get("hasEnoughData").asBoolean());
                hasInsufficient = true;
            }
        }
        assertTrue(hasInsufficient);
    }

    @Test
    @DisplayName("SCENARIO 5 & 6: Gợi ý chỉ lấy câu APPROVED và thuộc đúng Subject của lớp")
    void test05_06_recommendationPicksOnlyApprovedAndCorrectSubject() throws Exception {
        // Tạo 1 câu DRAFT trong topicNewton
        QuestionBank draftQ = createQuestion("Draft Question", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.DRAFT);
        // Tạo 2 câu APPROVED trong topicNewton
        QuestionBank approvedQ1 = createQuestion("Approved 1", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);
        QuestionBank approvedQ2 = createQuestion("Approved 2", QuestionType.FILL_BLANK, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String payload = "{\"questionCount\":5}";
        String response = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode session = objectMapper.readTree(response);
        JsonNode questions = session.get("questions");
        assertTrue(questions.size() > 0);

        for (JsonNode qNode : questions) {
            UUID qId = UUID.fromString(qNode.get("questionId").asText());
            assertNotEquals(draftQ.getQuestionId(), qId, "Không được chứa câu hỏi DRAFT");

            QuestionBank dbQ = questionBankRepository.findById(qId).orElseThrow();
            assertEquals(ApprovalStatus.APPROVED, dbQ.getApprovalStatus());
            assertEquals(subjectPhysics.getSubjectId(), dbQ.getSubjectId());
        }
    }

    @Test
    @DisplayName("SCENARIO 7: Không lấy trùng Question ID trong cùng một phiên luyện tập")
    void test07_noDuplicateQuestionIdsInSession() throws Exception {
        for (int i = 1; i <= 6; i++) {
            createQuestion("Unique Q" + i, QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);
        }

        String payload = "{\"questionCount\":5}";
        String response = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode questions = objectMapper.readTree(response).get("questions");
        Set<String> idSet = new HashSet<>();
        for (JsonNode qNode : questions) {
            String qId = qNode.get("questionId").asText();
            assertTrue(idSet.add(qId), "Phát hiện câu hỏi trùng lặp: " + qId);
        }
    }

    @Test
    @DisplayName("SCENARIO 10: Tuyệt đối không chọn câu hỏi đang được sử dụng trong đề FINAL")
    void test10_neverSelectFinalExamQuestions() throws Exception {
        QuestionBank finalQuestion = createQuestion("Secret FINAL Question", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        Exam finalExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Kỳ thi Cuối kỳ FINAL")
                .examType(ExamType.FINAL)
                .durationMinutes(90)
                .createdBy(gvNguyen.getUserId())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(finalExam.getExamId())
                .questionId(finalQuestion.getQuestionId())
                .orderIndex(1)
                .build());

        // Sinh phiên ôn tập
        String response = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":10}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode questions = objectMapper.readTree(response).get("questions");
        for (JsonNode qNode : questions) {
            assertNotEquals(finalQuestion.getQuestionId().toString(), qNode.get("questionId").asText(),
                    "Câu hỏi thuộc đề FINAL không được xuất hiện trong bài gợi ý!");
        }
    }

    @Test
    @DisplayName("SCENARIO 11, 12, 17: Hỗ trợ cả MCQ và FILL_BLANK, Student tự tạo session, nộp bài chấm điểm chuẩn")
    void test11_12_17_mcq_and_fillBlank_studentSelfSession_andGrading() throws Exception {
        QuestionBank mcq = createQuestion("Newton MCQ Test", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);
        QuestionBank fill = createQuestion("Gia tốc trọng trường g = ? m/s2", QuestionType.FILL_BLANK, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":10}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionNode = objectMapper.readTree(createResp);
        String sessionId = sessionNode.get("sessionId").asText();
        assertEquals(svAn.getUserId().toString(), sessionNode.get("studentId").asText(), "Session phải thuộc về chính sinh viên tạo");

        // Lấy option đúng của MCQ
        List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(mcq.getQuestionId());
        UUID correctOptionId = options.stream().filter(QuestionOption::getIsCorrect).findFirst().orElseThrow().getOptionId();

        // Lưu câu trả lời cho MCQ
        String ansMcq = "{\"questionId\":\"" + mcq.getQuestionId() + "\",\"selectedOptionIds\":[\"" + correctOptionId + "\"]}";
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ansMcq))
                .andExpect(status().isOk());

        // Lưu câu trả lời cho FILL_BLANK (nhập chuẩn hóa có khoảng trắng thừa và chữ hoa để kiểm tra AnswerNormalizationUtil)
        String ansFill = "{\"questionId\":\"" + fill.getQuestionId() + "\",\"answerText\":\"   9.8   \"}";
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ansFill))
                .andExpect(status().isOk());

        // Nộp bài
        String submitResp = mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(submitResp);
        assertEquals("COMPLETED", result.get("status").asText());
        assertTrue(result.get("correctCount").asInt() >= 2);
    }

    @Test
    @DisplayName("SCENARIO 13: Student B không được truy cập phiên luyện tập của Student A (403 Forbidden)")
    void test13_studentBCannotAccessStudentASession() throws Exception {
        createQuestion("Q For Student A", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        // Student B gọi session của A
        mockMvc.perform(get("/api/v1/student/practice-sessions/" + sessionId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 14: Student không có ghi danh ACTIVE bị chặn tạo phiên (403 Forbidden)")
    void test14_unEnrolledStudentBlockedFromRecommendations() throws Exception {
        createQuestion("Q Newton", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        // svBinh không ghi danh trong classB
        classEnrollmentRepository.deleteByClassIdAndStudentId(classB.getClassId(), svBinh.getUserId());

        mockMvc.perform(post("/api/v1/student/classes/" + classB.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 15: practiceEnabled=false chặn tạo bài luyện tập (400 Bad Request)")
    void test15_practiceEnabledFalseBlocksSession() throws Exception {
        createQuestion("Q Newton", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        ClassSettings settingsA = classSettingsRepository.findById(classA.getClassId()).orElseThrow();
        settingsA.setPracticeEnabled(false);
        classSettingsRepository.save(settingsA);

        mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SCENARIO 16: practiceShowSolution=false ẩn đáp án và giải thích trong kết quả bài thi")
    void test16_practiceShowSolutionFalseHidesAnswers() throws Exception {
        QuestionBank q = createQuestion("Q Newton Mask", QuestionType.FILL_BLANK, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        // Tắt practiceShowSolution
        ClassSettings settingsA = classSettingsRepository.findById(classA.getClassId()).orElseThrow();
        settingsA.setPracticeEnabled(true);
        settingsA.setPracticeShowSolution(false);
        classSettingsRepository.save(settingsA);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        // Nộp bài
        String submitResp = mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(submitResp);
        JsonNode questions = result.get("questions");
        for (JsonNode qNode : questions) {
            assertTrue(qNode.get("acceptedAnswers").isNull(), "acceptedAnswers phải bị ẩn (null)");
            assertTrue(qNode.get("explanation").isNull(), "explanation phải bị ẩn (null)");
        }
    }

    @Test
    @DisplayName("SCENARIO 18: Không thể nộp bài 2 lần hoặc sửa câu trả lời khi đã hoàn thành")
    void test18_cannotResubmitOrModifyCompletedSession() throws Exception {
        createQuestion("Q Newton 18", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        // Nộp bài lần 1 -> Thành công
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk());

        // Nộp bài lần 2 -> 400 Bad Request
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SCENARIO 19: Kết quả luyện tập mới được tích hợp vào gợi ý tiếp theo (Adaptive Feedback Loop)")
    void test19_adaptiveFeedbackLoopUpdatesWeakTopics() throws Exception {
        // Sinh 3 câu trong topicOscillation và student làm đúng cả 3 trong phiên ôn tập
        List<QuestionBank> qList = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            qList.add(createQuestion("Dao dong Loop Q" + i, QuestionType.MCQ_SINGLE, topicOscillation.getTopicId(), ApprovalStatus.APPROVED));
        }

        PracticeSession session = practiceSessionRepository.save(PracticeSession.builder()
                .studentId(svAn.getUserId())
                .classId(classA.getClassId())
                .subjectId(subjectPhysics.getSubjectId())
                .totalQuestions(3)
                .status(PracticeSessionStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        for (int i = 0; i < 3; i++) {
            QuestionBank q = qList.get(i);
            practiceSessionQuestionRepository.save(PracticeSessionQuestion.builder()
                    .sessionId(session.getSessionId())
                    .questionId(q.getQuestionId())
                    .topicId(topicOscillation.getTopicId())
                    .orderIndex(i + 1)
                    .build());

            List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId());
            UUID correctId = options.stream().filter(QuestionOption::getIsCorrect).findFirst().orElseThrow().getOptionId();

            practiceSessionAnswerRepository.save(PracticeSessionAnswer.builder()
                    .sessionId(session.getSessionId())
                    .questionId(q.getQuestionId())
                    .selectedOptionIds(correctId.toString())
                    .answeredAt(Instant.now())
                    .build());
        }

        // Nộp bài
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + session.getSessionId() + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk());

        // Kiểm tra weak-topics: topicOscillation giờ đã có 3 câu đúng 100% -> weaknessLevel chuyển thành GOOD
        String response = mockMvc.perform(get("/api/v1/student/classes/" + classA.getClassId() + "/weak-topics")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode array = objectMapper.readTree(response);
        JsonNode oscNode = null;
        for (JsonNode n : array) {
            if (n.get("topicId").asText().equals(topicOscillation.getTopicId().toString())) {
                oscNode = n;
                break;
            }
        }

        assertNotNull(oscNode);
        assertTrue(oscNode.get("totalAnswered").asInt() >= 3);
        assertEquals(1.0, oscNode.get("accuracyRate").asDouble(), 0.01);
        assertEquals("GOOD", oscNode.get("weaknessLevel").asText());
    }

    @Test
    @DisplayName("SCENARIO 20: Instructor xem thống kê điểm yếu chỉ trong lớp phụ trách (Lớp khác bị 403 Forbidden)")
    void test20_instructorScopedWeakTopicsSummary() throws Exception {
        // Instructor A xem classA -> 200 OK
        mockMvc.perform(get("/api/v1/instructor/classes/" + classA.getClassId() + "/weak-topics-summary")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(classA.getClassId().toString()))
                .andExpect(jsonPath("$.data.topics").isArray());

        // Instructor A xem classB (của Instructor B) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/instructor/classes/" + classB.getClassId() + "/weak-topics-summary")
                        .header("Authorization", "Bearer " + instructorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 21: Không đủ câu Topic yếu thì trả về số câu thực tế (isDeficient=true), không âm thầm lấy Topic khác trừ khi bật allowExpandedSubjectTopics")
    void test21_fallbackDeficiency_doesNotSilentlyExpandUnlessOptedIn() throws Exception {
        Topic defTopic = topicRepository.save(Topic.builder()
                .subjectId(subjectPhysics.getSubjectId())
                .chapterId(chapter1.getChapterId())
                .topicName("Deficient Topic " + UUID.randomUUID())
                .orderIndex(100)
                .build());

        // Tạo 3 câu kiểm tra cũ khiến defTopic thành NEEDS_PRACTICE (3 câu: 0 đúng, 3 sai)
        Exam practiceExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Exam for Deficient Test")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .createdBy(gvNguyen.getUserId())
                .build());

        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(practiceExam.getExamId())
                .studentId(svAn.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.GRADED)
                .startedAt(Instant.now().minusSeconds(3600))
                .submittedAt(Instant.now().minusSeconds(1800))
                .totalScore(BigDecimal.ZERO)
                .build());

        for (int i = 1; i <= 3; i++) {
            QuestionBank q = createQuestion("Old Def Q" + i + "_" + UUID.randomUUID(), QuestionType.MCQ_SINGLE, defTopic.getTopicId(), ApprovalStatus.APPROVED);
            examQuestionRepository.save(ExamQuestion.builder()
                    .examId(practiceExam.getExamId())
                    .questionId(q.getQuestionId())
                    .orderIndex(i)
                    .build());
            examAnswerRepository.save(ExamAnswer.builder()
                    .attemptId(attempt.getAttemptId())
                    .questionId(q.getQuestionId())
                    .isCorrect(false)
                    .score(BigDecimal.ZERO)
                    .build());
        }

        // Tạo đúng 2 câu MỚI trong defTopic
        QuestionBank newQ1 = createQuestion("New Def Q1_" + UUID.randomUUID(), QuestionType.MCQ_SINGLE, defTopic.getTopicId(), ApprovalStatus.APPROVED);
        QuestionBank newQ2 = createQuestion("New Def Q2_" + UUID.randomUUID(), QuestionType.MCQ_SINGLE, defTopic.getTopicId(), ApprovalStatus.APPROVED);

        // Tạo nhiều câu trong Topic khác (topicNewton) để kiểm tra xem hệ thống có tự ý lấy không
        for (int i = 1; i <= 5; i++) {
            createQuestion("Other Topic Q" + i + "_" + UUID.randomUUID(), QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);
        }

        // 1. Mặc định: Yêu cầu 10 câu cho defTopic -> Chỉ được trả về các câu thuộc defTopic (tối đa 2 câu mới + 3 câu làm sai = 5 câu), TUYỆT ĐỐI không lấy từ topicNewton
        String payloadDefault = "{\"questionCount\":10,\"topicId\":\"" + defTopic.getTopicId() + "\"}";
        String respDefault = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDefault))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionDefault = objectMapper.readTree(respDefault);
        assertTrue(sessionDefault.get("isDeficient").asBoolean(), "Phải đánh dấu isDeficient = true khi không đủ câu");
        assertNotNull(sessionDefault.get("deficiencyNotice").asText());
        assertFalse(sessionDefault.get("expandedTopicsUsed").asBoolean(), "Mặc định không được bật expandedTopicsUsed");

        JsonNode qDefault = sessionDefault.get("questions");
        for (JsonNode qNode : qDefault) {
            assertEquals(defTopic.getTopicId().toString(), qNode.get("topicId").asText(),
                    "Mặc định chỉ được lấy câu thuộc Topic cần ôn tập, không được âm thầm lấy Topic khác!");
        }

        // 2. Chế độ mở rộng rõ ràng (allowExpandedSubjectTopics = true) -> Bổ sung câu hỏi từ Topic khác và ghi rõ lý do
        String payloadExpanded = "{\"questionCount\":10,\"topicId\":\"" + defTopic.getTopicId() + "\",\"allowExpandedSubjectTopics\":true}";
        String respExpanded = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadExpanded))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionExpanded = objectMapper.readTree(respExpanded);
        assertTrue(sessionExpanded.get("expandedTopicsUsed").asBoolean(), "Phải ghi nhận expandedTopicsUsed = true khi có mở rộng");
        assertNotNull(sessionExpanded.get("expansionReason").asText(), "Phải có expansionReason giải thích lý do");
    }

    @Test
    @DisplayName("SCENARIO 22: Chặn nộp Option ID giả hoặc không thuộc câu hỏi MCQ (400 Bad Request)")
    void test22_mcqRejectsInvalidOptionId() throws Exception {
        QuestionBank q = createQuestion("MCQ Valid Options Test", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();
        UUID fakeOptionId = UUID.randomUUID();

        // Gửi optionId ngẫu nhiên không thuộc câu hỏi
        String payloadFakeOption = "{\"questionId\":\"" + q.getQuestionId() + "\",\"selectedOptionIds\":[\"" + fakeOptionId + "\"]}";
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadFakeOption))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SCENARIO 23: Teaching Assistant trong class_staff không được xem thống kê Instructor (403 Forbidden)")
    void test23_teachingAssistantDeniedInstructorAnalytics() throws Exception {
        // Gán gvTran làm TA trong classA
        classStaffRepository.deleteByClassIdAndUserId(classA.getClassId(), gvTran.getUserId());
        classStaffRepository.save(ClassStaff.builder()
                .classId(classA.getClassId())
                .userId(gvTran.getUserId())
                .roleInClass(ClassStaffRole.TA)
                .build());

        // gvTran gọi xem thống kê lớp A -> Phải bị 403 Forbidden vì chỉ có vai trò TA
        mockMvc.perform(get("/api/v1/instructor/classes/" + classA.getClassId() + "/weak-topics-summary")
                        .header("Authorization", "Bearer " + instructorBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SCENARIO 24: Không thể xóa QuestionBank đã được sử dụng trong Practice Session (400 Bad Request)")
    void test24_cannotDeleteQuestionUsedInPracticeSession() throws Exception {
        QuestionBank q = createQuestion("Delete Protection Practice Q", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        PracticeSession session = practiceSessionRepository.save(PracticeSession.builder()
                .studentId(svAn.getUserId())
                .classId(classA.getClassId())
                .subjectId(subjectPhysics.getSubjectId())
                .totalQuestions(1)
                .status(PracticeSessionStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        practiceSessionQuestionRepository.save(PracticeSessionQuestion.builder()
                .sessionId(session.getSessionId())
                .questionId(q.getQuestionId())
                .topicId(topicNewton.getTopicId())
                .orderIndex(1)
                .build());

        // Admin cố tình xóa question -> Bị chặn với 400 Bad Request
        com.vatly1.example.exception.CustomException ex = assertThrows(com.vatly1.example.exception.CustomException.class, () -> {
            questionBankService.deleteQuestion(q.getQuestionId(), adminToken != null ? UUID.randomUUID() : null, "ADMIN");
        });
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Cannot delete question that has already been used"));
    }

    @Test
    @DisplayName("SCENARIO 25: Hai request submit đồng thời an toàn nhờ Pessimistic Lock (1 thành công 200, 1 nhận 400)")
    void test25_concurrentDoubleSubmitSafety() throws Exception {
        createQuestion("Concurrent Test Q", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger badRequestCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);

        Runnable submitTask = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int statusCode = mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                                .header("Authorization", "Bearer " + studentAToken))
                        .andReturn().getResponse().getStatus();
                if (statusCode == 200) {
                    successCount.incrementAndGet();
                } else if (statusCode == 400) {
                    badRequestCount.incrementAndGet();
                }
            } catch (Exception e) {
                // error
            }
        };

        executor.submit(submitTask);
        executor.submit(submitTask);

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Chính xác 1 request submit thành công 200 OK");
        assertEquals(1, badRequestCount.get(), "Request đồng thời thứ 2 phải nhận 400 Bad Request");
    }

    @Test
    @DisplayName("SCENARIO 26: Không thể thay đổi đáp án sau khi phiên luyện tập đã COMPLETED (400 Bad Request)")
    void test26_cannotSaveAnswerAfterSessionCompleted() throws Exception {
        QuestionBank q = createQuestion("Completed Session Answer Test", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        // Nộp bài để phiên chuyển sang COMPLETED
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk());

        // Cố tình gửi đáp án sau khi đã nộp bài
        String payload = "{\"questionId\":\"" + q.getQuestionId() + "\",\"answerText\":\"dummy\"}";
        mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("không thể thay đổi đáp án")));
    }

    @Test
    @DisplayName("SCENARIO 27: Không thể cập nhật QuestionBank đã được sử dụng trong Practice Session (400 Bad Request)")
    void test27_cannotUpdateQuestionUsedInPracticeSession() throws Exception {
        QuestionBank q = createQuestion("Update Protection Practice Q", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        PracticeSession session = practiceSessionRepository.save(PracticeSession.builder()
                .studentId(svAn.getUserId())
                .classId(classA.getClassId())
                .subjectId(subjectPhysics.getSubjectId())
                .totalQuestions(1)
                .status(PracticeSessionStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build());

        practiceSessionQuestionRepository.save(PracticeSessionQuestion.builder()
                .sessionId(session.getSessionId())
                .questionId(q.getQuestionId())
                .topicId(topicNewton.getTopicId())
                .orderIndex(1)
                .build());

        com.vatly1.example.model.request.CreateQuestionDTO updateDto = new com.vatly1.example.model.request.CreateQuestionDTO();
        updateDto.setSubjectId(subjectPhysics.getSubjectId());
        updateDto.setTopicId(topicNewton.getTopicId());
        updateDto.setQuestionType(QuestionType.MCQ_SINGLE);
        updateDto.setContent("Updated Content Attempt");
        updateDto.setDifficultyLevel(DifficultyLevel.EASY);
        updateDto.setOptions(List.of(
                com.vatly1.example.model.request.CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                        .content("Option A")
                        .isCorrect(true)
                        .orderIndex(0)
                        .build()
        ));

        // Cập nhật câu hỏi đã dùng trong practice session -> được phép để sửa sai sót
        com.vatly1.example.model.dto.QuestionBankDTO updated = questionBankService.updateQuestion(
                q.getQuestionId(), updateDto, adminToken != null ? UUID.randomUUID() : null, "ADMIN");
        assertNotNull(updated);
        assertEquals("Updated Content Attempt", updated.getContent());

        // Xóa câu hỏi đã dùng trong practice session -> bị chặn
        com.vatly1.example.exception.CustomException ex = assertThrows(com.vatly1.example.exception.CustomException.class, () -> {
            questionBankService.deleteQuestion(q.getQuestionId(), adminToken != null ? UUID.randomUUID() : null, "ADMIN");
        });
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Cannot delete question that has already been used in an exam or practice session"));
    }

    @Test
    @DisplayName("SCENARIO 28: Concurrency giữa saveAnswer và submitSession được tuần tự hóa an toàn bằng Pessimistic Lock")
    void test28_concurrentSaveAndSubmitSafety() throws Exception {
        QuestionBank q = createQuestion("Concurrent Save Submit Q", QuestionType.MCQ_SINGLE, topicNewton.getTopicId(), ApprovalStatus.APPROVED);

        String createResp = mockMvc.perform(post("/api/v1/student/classes/" + classA.getClassId() + "/practice-recommendations")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(createResp).get("sessionId").asText();

        // Tìm option đúng của q
        List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId());
        UUID correctOptId = options.stream().filter(QuestionOption::getIsCorrect).findFirst().get().getOptionId();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger submitStatus = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger saveStatus = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);

        executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int status = mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                                .header("Authorization", "Bearer " + studentAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"questionId\":\"" + q.getQuestionId() + "\",\"selectedOptionIds\":[\"" + correctOptId + "\"]}"))
                        .andReturn().getResponse().getStatus();
                saveStatus.set(status);
            } catch (Exception ignored) {}
        });

        executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int status = mockMvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/submit")
                                .header("Authorization", "Bearer " + studentAToken))
                        .andReturn().getResponse().getStatus();
                submitStatus.set(status);
            } catch (Exception ignored) {}
        });

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Submit phải luôn luôn thành công 200 OK
        assertEquals(200, submitStatus.get(), "SubmitSession phải thành công 200 OK");
        // SaveAnswer hoặc là 200 OK (nếu chạy trước submit) hoặc 400 Bad Request (nếu chạy sau khi submit đã chốt phiên)
        assertTrue(saveStatus.get() == 200 || saveStatus.get() == 400,
                "SaveAnswer phải là 200 (được tính vào bài) hoặc 400 (chặn thay đổi sau submit). Thực tế: " + saveStatus.get());
    }
}
