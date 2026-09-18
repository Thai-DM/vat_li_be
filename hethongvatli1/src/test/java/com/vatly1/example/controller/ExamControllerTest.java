package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.User;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import org.junit.jupiter.api.Assertions;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private String adminToken;
    private String instructorToken;
    private String instructorBToken;
    private String studentToken;
    private String studentBToken;
    private String classId;
    private String subjectId;
    private UUID questionId;
    private UUID correctOptionId;
    private UUID wrongOptionId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        instructorBToken = signin("gv_tran", "gv_tran123456");
        studentToken = signin("sv_an", "sv_an123456");
        studentBToken = signin("sv_binh", "sv_binh123456");

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        Class targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()) && "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();
        classId = targetClass.getClassId().toString();
        subjectId = targetClass.getSubjectId().toString();

        QuestionBank q = QuestionBank.builder()
                .subjectId(UUID.fromString(subjectId))
                .topicId(UUID.randomUUID())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Gia tốc rơi tự do xấp xỉ bằng bao nhiêu m/s^2?")
                .difficultyLevel(DifficultyLevel.EASY)
                .build();
        QuestionBank savedQ = questionBankRepository.save(q);
        questionId = savedQ.getQuestionId();

        QuestionOption opt1 = QuestionOption.builder()
                .questionId(questionId)
                .optionText("9.8 m/s^2")
                .isCorrect(true)
                .orderIndex(1)
                .build();
        QuestionOption opt2 = QuestionOption.builder()
                .questionId(questionId)
                .optionText("12.5 m/s^2")
                .isCorrect(false)
                .orderIndex(2)
                .build();
        QuestionOption savedOpt1 = questionOptionRepository.save(opt1);
        QuestionOption savedOpt2 = questionOptionRepository.save(opt2);
        correctOptionId = savedOpt1.getOptionId();
        wrongOptionId = savedOpt2.getOptionId();
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

    private String createExamHelper(String token, String cId, String title, Instant start, Instant end) throws Exception {
        return createExamHelper(token, cId, title, "QUIZ", start, end);
    }

    private String createExamHelper(String token, String cId, String title, String examType, Instant start, Instant end) throws Exception {
        String payload = """
            {
                "classId": "%s",
                "title": "%s",
                "examType": "%s",
                "durationMinutes": 45,
                "startTime": %s,
                "endTime": %s
            }
            """.formatted(cId, title, examType,
                start != null ? "\"" + start.toString() + "\"" : "null",
                end != null ? "\"" + end.toString() + "\"" : "null");

        String res = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("data").get("examId").asText();
    }

    @Test
    @DisplayName("EXAM-01: Giảng viên tạo đề thi thành công cho lớp học phần")
    void testExam01_CreateExamSuccess() throws Exception {
        String title = "Kiểm tra 15 phút Động học " + UUID.randomUUID();
        String examId = createExamHelper(instructorToken, classId, title, null, null);
        Assertions.assertNotNull(examId);

        mockMvc.perform(get("/api/v1/exams/" + examId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.durationMinutes").value(45));
    }

    @Test
    @DisplayName("EXAM-02: Giảng viên thêm câu hỏi trắc nghiệm vào đề thi")
    void testExam02_AddQuestionToExam() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi thêm câu hỏi", null, null);

        String payload = """
            {
                "questionId": "%s",
                "scoreWeight": 10.0,
                "orderIndex": 1
            }
            """.formatted(questionId);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/exams/" + examId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(1));
    }

    @Test
    @DisplayName("EXAM-03: Sinh viên bắt đầu làm bài thi (tạo Attempt)")
    void testExam03_StartAttemptSuccess() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi kiểm tra bắt đầu", null, null);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.attemptId").isNotEmpty());
    }

    @Test
    @DisplayName("EXAM-04: Sinh viên lưu câu trả lời khi đang làm bài")
    void testExam04_SubmitAnswerSuccess() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi lưu đáp án", null, null);

        // Add question to exam
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\", \"scoreWeight\": 10.0}"))
                .andExpect(status().isOk());

        // Student starts attempt
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Submit answer
        String answerPayload = """
            {
                "questionId": "%s",
                "selectedOptionIds": ["%s"]
            }
            """.formatted(questionId, correctOptionId);

        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("EXAM-05: Sinh viên nộp bài thi - Hệ thống tự động chấm điểm MCQ chính xác")
    void testExam05_SubmitAttemptAndAutoGrade() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi tự động chấm điểm", null, null);

        // Add question with weight 10.0
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\", \"scoreWeight\": 10.0}"))
                .andExpect(status().isOk());

        // Student starts attempt
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Submit correct answer
        String answerPayload = """
            {
                "questionId": "%s",
                "selectedOptionIds": ["%s"]
            }
            """.formatted(questionId, correctOptionId);

        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload))
                .andExpect(status().isOk());

        // Submit attempt
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.totalScore").value(10.0));

        ExamAttempt dbAttempt = examAttemptRepository.findById(UUID.fromString(attemptId)).orElseThrow();
        Assertions.assertEquals(AttemptStatus.GRADED, dbAttempt.getStatus());
        Assertions.assertEquals(0, new BigDecimal("10.00").compareTo(dbAttempt.getTotalScore()));
    }

    @Test
    @DisplayName("EXAM-06: Chặn sinh viên làm bài lần 2 cho cùng 1 đề thi (409 Conflict)")
    void testExam06_PreventDuplicateAttempt() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi chặn thi 2 lần", null, null);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());

        // Second attempt must return 409 Conflict
        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("EXAM-07: (IDOR) Sinh viên B cố xem chi tiết bài thi của Sinh viên A bị chặn 403")
    void testExam07_IdorPreventStudentViewingAnotherAttempt() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Bài thi IDOR SV", null, null);

        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Student B tries to view Student A's attempt
        mockMvc.perform(get("/api/v1/exams/attempts/" + attemptId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXAM-08: Chặn sinh viên làm bài khi đề thi đã kết thúc thời gian mở (400 Bad Request)")
    void testExam08_PreventAttemptWhenExamExpired() throws Exception {
        Instant pastStart = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant pastEnd = Instant.now().minus(1, ChronoUnit.HOURS);

        String examId = createExamHelper(instructorToken, classId, "Đề thi đã hết hạn", pastStart, pastEnd);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("EXAM-09: (IDOR) Giảng viên B cố thêm câu hỏi vào đề thi của Giảng viên A bị chặn 403")
    void testExam09_IdorPreventInstructorModifyingAnotherClassExam() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi thuộc GV Nguyen", null, null);

        String payload = """
            {
                "questionId": "%s",
                "scoreWeight": 5.0
            }
            """.formatted(questionId);

        // Instructor B (gv_tran) attempts to modify exam in Instructor A's class
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXAM-10: Chặn sinh viên gửi câu trả lời sau khi đã nộp bài (400 Bad Request)")
    void testExam10_PreventAnswerAfterSubmit() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi chặn sửa sau khi nộp", null, null);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isOk());

        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Submit attempt
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Try to submit answer again
        String answerPayload = """
            {
                "questionId": "%s",
                "selectedOptionIds": ["%s"]
            }
            """.formatted(questionId, correctOptionId);

        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("EXAM-11: (Concurrency) Hai luồng đồng thời gọi startAttempt cho cùng 1 sinh viên -> 1x 201, 1x 409")
    void testExam11_ConcurrentStartAttempt() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi test concurrency đa luồng", null, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> startAttemptTask = () -> {
            try {
                return mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                                .header("Authorization", "Bearer " + studentToken))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            } catch (Exception e) {
                return 500;
            }
        };

        List<Callable<Integer>> tasks = List.of(startAttemptTask, startAttemptTask);
        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> statusCodes = new ArrayList<>();
        for (Future<Integer> f : results) {
            statusCodes.add(f.get());
        }

        long count201 = statusCodes.stream().filter(s -> s == 201).count();
        long count409 = statusCodes.stream().filter(s -> s == 409).count();
        long count500 = statusCodes.stream().filter(s -> s >= 500).count();

        Assertions.assertEquals(1, count201, "Chính xác 1 luồng thành công (201 Created)");
        Assertions.assertEquals(1, count409, "Chính xác 1 luồng bị chặn do tranh chấp (409 Conflict)");
        Assertions.assertEquals(0, count500, "Tuyệt đối không có lỗi 500 Internal Server Error");
    }

    @Test
    @DisplayName("EXAM-12: Tra cứu đề thi không tồn tại trả về 404 Not Found")
    void testExam12_ExamNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/exams/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EXAM-13: [IDOR Ghi] Sinh viên B cố gửi đáp án hoặc nộp bài vào Attempt của Sinh viên A bị chặn 403")
    void testExam13_IdorPreventStudentModifyingOrSubmittingAnotherAttempt() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi IDOR Ghi", null, null);

        // Add question
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isOk());

        // Student A starts attempt
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptIdOfA = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // 1. Student B tries to submit answer to Student A's attempt
        String answerPayload = """
            {
                "questionId": "%s",
                "selectedOptionIds": ["%s"]
            }
            """.formatted(questionId, correctOptionId);

        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptIdOfA + "/answers")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload))
                .andExpect(status().isForbidden());

        // 2. Student B tries to submit/complete Student A's attempt
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptIdOfA + "/submit")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EXAM-14: [Hết hạn khi đang thi] Chặn gửi đáp án và nộp bài sau khi thời gian đề thi kết thúc (400 Bad Request)")
    void testExam14_PreventSubmitWhenExamEnded() throws Exception {
        // Start time was 2 hours ago, end time is 1 second in the future
        Instant pastStart = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant futureEnd = Instant.now().plus(1, ChronoUnit.SECONDS);

        String examId = createExamHelper(instructorToken, classId, "Đề thi sắp hết hạn", pastStart, futureEnd);

        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\"}"))
                .andExpect(status().isOk());

        // Student starts attempt before deadline
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        // Wait for deadline to pass
        Thread.sleep(1200);

        // Try to submit answer after deadline
        String answerPayload = """
            {
                "questionId": "%s",
                "selectedOptionIds": ["%s"]
            }
            """.formatted(questionId, correctOptionId);

        mockMvc.perform(post("/api/v1/exams/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload))
                .andExpect(status().isBadRequest());

        // Try to submit attempt after deadline
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("EXAM-15: [Bài thi luyện tập - PRACTICE] Cho phép sinh viên làm nhiều lượt (multi-attempt), mỗi lượt tăng attemptNumber")
    void testExam15_PracticeExam_MultipleAttemptsAllowed() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi luyện tập Cơ học " + UUID.randomUUID(), "PRACTICE", null, null);

        // Add question to exam
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\", \"scoreWeight\": 10.0}"))
                .andExpect(status().isOk());

        // Lượt 1: Bắt đầu làm bài -> attemptNumber = 1
        String attemptRes1 = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        String attemptId1 = objectMapper.readTree(attemptRes1).get("data").get("attemptId").asText();

        // Cố bắt đầu lượt mới khi lượt 1 đang IN_PROGRESS -> 409 Conflict
        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());

        // Nộp bài lượt 1 -> 200 OK, status = GRADED
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId1 + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.attemptNumber").value(1));

        // Lượt 2: Sau khi lượt 1 hoàn thành, cho phép bắt đầu lượt 2 -> attemptNumber = 2
        String attemptRes2 = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(2))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        String attemptId2 = objectMapper.readTree(attemptRes2).get("data").get("attemptId").asText();
        Assertions.assertNotEquals(attemptId1, attemptId2);

        // Nộp bài lượt 2
        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId2 + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptNumber").value(2));

        // Tra cứu my-attempt trả về lượt mới nhất (attemptNumber = 2)
        mockMvc.perform(get("/api/v1/exams/" + examId + "/my-attempt")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptNumber").value(2))
                .andExpect(jsonPath("$.data.attemptId").value(attemptId2));
    }

    @Test
    @DisplayName("EXAM-16: [Bài thi chính thức - MIDTERM/FINAL] Nghiêm ngặt chỉ cho phép 1 lượt thi duy nhất, chặn lượt 2 bằng 409 Conflict")
    void testExam16_OfficialExam_StrictSingleAttempt() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Thi Giữa kỳ Vật lý 1 " + UUID.randomUUID(), "MIDTERM", null, null);

        // Add question to exam
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\", \"scoreWeight\": 10.0}"))
                .andExpect(status().isOk());

        // Lượt 1: Bắt đầu và nộp bài
        String attemptRes = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(attemptRes).get("data").get("attemptId").asText();

        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Cố gắng làm lại bài thi chính thức (lượt 2) sau khi đã nộp -> Bị từ chối triệt để 409 Conflict
        mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("EXAM-17: Tra cứu toàn bộ lịch sử các lượt thi của sinh viên (GET /api/v1/exams/{examId}/my-attempts)")
    void testExam17_GetMyAttempts() throws Exception {
        String examId = createExamHelper(instructorToken, classId, "Đề thi luyện tập lịch sử lượt thi " + UUID.randomUUID(), "PRACTICE", null, null);

        // Add question to exam
        mockMvc.perform(post("/api/v1/exams/" + examId + "/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questionId + "\", \"scoreWeight\": 10.0}"))
                .andExpect(status().isOk());

        // Lượt 1: Bắt đầu và nộp bài
        String attemptRes1 = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId1 = objectMapper.readTree(attemptRes1).get("data").get("attemptId").asText();

        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId1 + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Lượt 2: Bắt đầu và nộp bài
        String attemptRes2 = mockMvc.perform(post("/api/v1/exams/" + examId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId2 = objectMapper.readTree(attemptRes2).get("data").get("attemptId").asText();

        mockMvc.perform(put("/api/v1/exams/attempts/" + attemptId2 + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Tra cứu my-attempts
        mockMvc.perform(get("/api/v1/exams/" + examId + "/my-attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].attemptId").value(attemptId1))
                .andExpect(jsonPath("$.data[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.data[1].attemptId").value(attemptId2))
                .andExpect(jsonPath("$.data[1].attemptNumber").value(2));
    }
}