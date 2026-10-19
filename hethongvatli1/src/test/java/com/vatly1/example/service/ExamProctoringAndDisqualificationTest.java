package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.ProctoringMode;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.ViolationSeverity;
import com.vatly1.example.entity.enums.ViolationType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamAttemptSummaryDTO;
import com.vatly1.example.model.dto.ExamViolationLogDTO;
import com.vatly1.example.model.dto.ViolationResultDTO;
import com.vatly1.example.model.request.BatchSubmitAnswerDTO;
import com.vatly1.example.model.request.DisqualifyAttemptRequestDTO;
import com.vatly1.example.model.request.LogViolationRequestDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IExamViolationLogRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class ExamProctoringAndDisqualificationTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IExamViolationLogRepository examViolationLogRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    private User instructor;
    private User student;
    private Class testClass;
    private Subject testSubject;
    private Topic testTopic;
    private QuestionBank testQuestion;

    @BeforeEach
    void setUp() {
        examViolationLogRepository.deleteAll();
        examAttemptRepository.deleteAll();
        examQuestionRepository.deleteAll();
        examRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        instructor = userRepository.save(User.builder()
                .username("instr_" + suffix)
                .passwordHash("hashed")
                .role(UserRole.INSTRUCTOR)
                .email("instr_" + suffix + "@test.com")
                .build());

        student = userRepository.save(User.builder()
                .username("stud_" + suffix)
                .passwordHash("hashed")
                .role(UserRole.STUDENT)
                .email("stud_" + suffix + "@test.com")
                .build());

        testSubject = subjectRepository.save(Subject.builder()
                .subjectCode("SUB_" + suffix)
                .subjectName("Subject " + suffix)
                .build());

        testTopic = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .topicName("Topic " + suffix)
                .orderIndex(1)
                .build());

        testClass = classRepository.save(Class.builder()
                .subjectId(testSubject.getSubjectId())
                .instructorId(instructor.getUserId())
                .classCode("CLS_" + suffix)
                .build());

        testQuestion = questionBankRepository.save(QuestionBank.builder()
                .subjectId(testSubject.getSubjectId())
                .topicId(testTopic.getTopicId())
                .content("Câu hỏi kiểm tra vi phạm")
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(instructor.getUserId())
                .build());
    }

    private Exam createExamWithMode(ProctoringMode mode, int maxViolations, boolean autoBan) {
        Exam exam = examRepository.save(Exam.builder()
                .classId(testClass.getClassId())
                .title("Kỳ thi " + mode)
                .examType(ExamType.QUIZ)
                .durationMinutes(60)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(50, ChronoUnit.MINUTES))
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .proctoringMode(mode)
                .maxViolationsAllowed(maxViolations)
                .autoBanOnLimit(autoBan)
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(testQuestion.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.ONE)
                .build());

        return exam;
    }

    private ExamAttempt createInProgressAttempt(Exam exam) {
        return examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .startedAt(Instant.now())
                .status(AttemptStatus.IN_PROGRESS)
                .violationCount(0)
                .isDisqualified(false)
                .build());
    }

    @Test
    @DisplayName("Chế độ NONE: Không ghi nhận log vi phạm và không tăng bộ đếm")
    void testLogViolation_NoneMode_Ignored() {
        Exam exam = createExamWithMode(ProctoringMode.NONE, 5, false);
        ExamAttempt attempt = createInProgressAttempt(exam);

        LogViolationRequestDTO req = LogViolationRequestDTO.builder()
                .violationType(ViolationType.TAB_SWITCH)
                .description("Chuyển tab")
                .build();

        ViolationResultDTO result = examService.logViolation(attempt.getAttemptId(), req, student.getUserId());

        assertEquals(0, result.getCurrentViolationCount());
        assertFalse(result.getIsDisqualified());
        assertTrue(result.getMessage().contains("NONE"));
        assertEquals(0, examViolationLogRepository.countByAttemptId(attempt.getAttemptId()));
    }

    @Test
    @DisplayName("Chế độ BASIC/STRICT: Tăng bộ đếm vi phạm và lưu log chi tiết vào CSDL")
    void testLogViolation_BasicMode_Recorded() {
        Exam exam = createExamWithMode(ProctoringMode.BASIC, 5, false);
        ExamAttempt attempt = createInProgressAttempt(exam);

        LogViolationRequestDTO req1 = LogViolationRequestDTO.builder()
                .violationType(ViolationType.TAB_SWITCH)
                .description("Rời khỏi tab thi 5 giây")
                .metadataJson("{\"seconds\": 5}")
                .build();

        ViolationResultDTO res1 = examService.logViolation(attempt.getAttemptId(), req1, student.getUserId());
        assertEquals(1, res1.getCurrentViolationCount());
        assertFalse(res1.getIsDisqualified());

        LogViolationRequestDTO req2 = LogViolationRequestDTO.builder()
                .violationType(ViolationType.WINDOW_BLUR)
                .description("Mất focus chuột")
                .build();

        ViolationResultDTO res2 = examService.logViolation(attempt.getAttemptId(), req2, student.getUserId());
        assertEquals(2, res2.getCurrentViolationCount());
        assertFalse(res2.getIsDisqualified());

        List<ExamViolationLogDTO> logs = examService.getAttemptViolations(attempt.getAttemptId(), student.getUserId(), "STUDENT");
        assertEquals(2, logs.size());
        assertEquals(ViolationType.TAB_SWITCH, logs.get(0).getViolationType());
        assertEquals(ViolationType.WINDOW_BLUR, logs.get(1).getViolationType());
    }

    @Test
    @DisplayName("Chế độ STRICT với autoBanOnLimit = true: Tự động đình chỉ bài thi khi vượt ngưỡng")
    void testLogViolation_StrictAutoBanOnThreshold() {
        Exam exam = createExamWithMode(ProctoringMode.STRICT, 2, true);
        ExamAttempt attempt = createInProgressAttempt(exam);

        // Lần 1
        examService.logViolation(attempt.getAttemptId(), LogViolationRequestDTO.builder()
                .violationType(ViolationType.FULLSCREEN_EXIT)
                .build(), student.getUserId());

        ExamAttempt after1 = examAttemptRepository.findById(attempt.getAttemptId()).orElseThrow();
        assertEquals(1, after1.getViolationCount());
        assertEquals(AttemptStatus.IN_PROGRESS, after1.getStatus());
        assertFalse(after1.getIsDisqualified());

        // Lần 2 -> Chạm ngưỡng 2 vi phạm -> Auto ban
        ViolationResultDTO res2 = examService.logViolation(attempt.getAttemptId(), LogViolationRequestDTO.builder()
                .violationType(ViolationType.DEVTOOLS_OPENED)
                .description("Mở DevTools F12")
                .build(), student.getUserId());

        assertTrue(res2.getIsDisqualified());
        assertEquals(2, res2.getCurrentViolationCount());

        ExamAttempt after2 = examAttemptRepository.findById(attempt.getAttemptId()).orElseThrow();
        assertEquals(AttemptStatus.DISQUALIFIED, after2.getStatus());
        assertTrue(after2.getIsDisqualified());
        assertNotNull(after2.getDisqualifiedReason());
    }

    @Test
    @DisplayName("Sinh viên bị DISQUALIFIED bị chặn ngay lập tức khi gửi bài, nộp bài hoặc lấy câu hỏi")
    void testDisqualifiedStudent_BlockedFromOperations() {
        Exam exam = createExamWithMode(ProctoringMode.BASIC, 3, false);
        ExamAttempt attempt = createInProgressAttempt(exam);

        // Giảng viên cấm thi
        examService.disqualifyAttempt(exam.getExamId(), attempt.getAttemptId(),
                DisqualifyAttemptRequestDTO.builder().reason("Gian lận chuyển tab nhiều lần").build(),
                instructor.getUserId(), "INSTRUCTOR");

        // 1. Thử submitAnswer -> Phải ném 403 FORBIDDEN
        SubmitAnswerDTO answerDTO = SubmitAnswerDTO.builder()
                .questionId(testQuestion.getQuestionId())
                .selectedOptionIds(List.of())
                .build();

        CustomException ex1 = assertThrows(CustomException.class, () ->
                examService.submitAnswer(attempt.getAttemptId(), answerDTO, student.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex1.getHttpStatus());
        assertTrue(ex1.getMessage().contains("đình chỉ"));

        // 2. Thử autosaveAnswers -> Phải ném 403 FORBIDDEN
        BatchSubmitAnswerDTO batchDTO = BatchSubmitAnswerDTO.builder()
                .answers(List.of(answerDTO))
                .build();

        CustomException ex2 = assertThrows(CustomException.class, () ->
                examService.autosaveAnswers(attempt.getAttemptId(), batchDTO, student.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex2.getHttpStatus());

        // 3. Thử submitAttempt -> Phải ném 403 FORBIDDEN
        CustomException ex3 = assertThrows(CustomException.class, () ->
                examService.submitAttempt(attempt.getAttemptId(), student.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex3.getHttpStatus());

        // 4. Thử getAttemptQuestions với role STUDENT -> Phải ném 403 FORBIDDEN
        CustomException ex4 = assertThrows(CustomException.class, () ->
                examService.getAttemptQuestions(attempt.getAttemptId(), student.getUserId(), "STUDENT"));
        assertEquals(HttpStatus.FORBIDDEN, ex4.getHttpStatus());
    }

    @Test
    @DisplayName("Giảng viên cấm thi (Disqualify) và Khôi phục bài thi (Reinstate) thành công")
    void testInstructorDisqualifyAndReinstate_Flow() {
        Exam exam = createExamWithMode(ProctoringMode.BASIC, 5, false);
        ExamAttempt attempt = createInProgressAttempt(exam);

        // 1. Giảng viên cấm thi
        ExamAttemptDTO disqualified = examService.disqualifyAttempt(exam.getExamId(), attempt.getAttemptId(),
                DisqualifyAttemptRequestDTO.builder().reason("Tra cứu tài liệu ngoài").build(),
                instructor.getUserId(), "INSTRUCTOR");

        assertEquals(AttemptStatus.DISQUALIFIED, disqualified.getStatus());
        assertTrue(disqualified.getIsDisqualified());
        assertEquals("Tra cứu tài liệu ngoài", disqualified.getDisqualifiedReason());

        // Kiểm tra danh sách attempts của giảng viên thấy vi phạm và trạng thái cấm
        List<ExamAttemptSummaryDTO> summaries = examService.getExamAttempts(exam.getExamId(), instructor.getUserId(), "INSTRUCTOR");
        assertEquals(1, summaries.size());
        assertTrue(summaries.get(0).getIsDisqualified());
        assertEquals(AttemptStatus.DISQUALIFIED, summaries.get(0).getStatus());

        // 2. Giảng viên mở lại bài thi (Reinstate / Hủy cấm)
        ExamAttemptDTO reinstated = examService.reinstateAttempt(exam.getExamId(), attempt.getAttemptId(),
                instructor.getUserId(), "INSTRUCTOR");

        assertEquals(AttemptStatus.IN_PROGRESS, reinstated.getStatus());
        assertFalse(reinstated.getIsDisqualified());
        assertNull(reinstated.getDisqualifiedReason());

        // Sinh viên bây giờ có thể lưu câu trả lời bình thường
        SubmitAnswerDTO answerDTO = SubmitAnswerDTO.builder()
                .questionId(testQuestion.getQuestionId())
                .selectedOptionIds(List.of())
                .build();
        assertDoesNotThrow(() -> examService.submitAnswer(attempt.getAttemptId(), answerDTO, student.getUserId()));
    }

    @Test
    @DisplayName("Giảng viên mở lại bài thi khi lượt thi đã HẾT GIỜ -> Tự động nộp bài và chấm điểm theo các câu đã lưu (GRADED)")
    void testInstructorReinstate_WhenTimeExpired_AutoSubmitsAndGrades() {
        Exam exam = createExamWithMode(ProctoringMode.BASIC, 5, false);
        // Tạo attempt đã bắt đầu cách đây 70 phút (với thời lượng 60 phút) -> Đã hết thời gian làm bài
        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .startedAt(Instant.now().minus(70, ChronoUnit.MINUTES))
                .status(AttemptStatus.IN_PROGRESS)
                .violationCount(1)
                .isDisqualified(false)
                .build());

        // Giảng viên cấm thi
        examService.disqualifyAttempt(exam.getExamId(), attempt.getAttemptId(),
                DisqualifyAttemptRequestDTO.builder().reason("Nghi vấn gian lận").build(),
                instructor.getUserId(), "INSTRUCTOR");

        ExamAttempt afterBan = examAttemptRepository.findById(attempt.getAttemptId()).orElseThrow();
        assertEquals(AttemptStatus.DISQUALIFIED, afterBan.getStatus());
        assertTrue(afterBan.getIsDisqualified());

        // Giảng viên mở lại bài thi -> Vì đã hết giờ làm bài của lượt thi, hệ thống phải tự động nộp bài (GRADED)
        ExamAttemptDTO reinstated = examService.reinstateAttempt(exam.getExamId(), attempt.getAttemptId(),
                instructor.getUserId(), "INSTRUCTOR");

        // Không còn hiển thị "đang làm" (IN_PROGRESS) nữa mà chuyển thành "GRADED"
        assertEquals(AttemptStatus.GRADED, reinstated.getStatus());
        assertFalse(reinstated.getIsDisqualified());
        assertNull(reinstated.getDisqualifiedReason());
        assertNotNull(reinstated.getSubmittedAt());
        assertNotNull(reinstated.getTotalScore());
    }

    @Test
    @DisplayName("Sinh viên gửi câu trả lời khi lượt thi đã HẾT GIỜ -> Bị từ chối với mã BAD_REQUEST")
    void testSubmitAnswer_WhenTimeExpired_ThrowsException() {
        Exam exam = createExamWithMode(ProctoringMode.BASIC, 5, false);
        // Attempt bắt đầu cách đây 70 phút (thời lượng exam 60 phút)
        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .startedAt(Instant.now().minus(70, ChronoUnit.MINUTES))
                .status(AttemptStatus.IN_PROGRESS)
                .violationCount(0)
                .isDisqualified(false)
                .build());

        SubmitAnswerDTO answerDTO = SubmitAnswerDTO.builder()
                .questionId(testQuestion.getQuestionId())
                .selectedOptionIds(List.of())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.submitAnswer(attempt.getAttemptId(), answerDTO, student.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("expired") || ex.getMessage().contains("kết thúc") || ex.getMessage().contains("hết"));
    }
}
