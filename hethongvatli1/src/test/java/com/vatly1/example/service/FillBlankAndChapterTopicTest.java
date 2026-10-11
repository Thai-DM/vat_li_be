package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionAcceptedAnswer;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.dto.QuestionOptionDTO;
import com.vatly1.example.model.dto.StudentExamQuestionDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.utils.AnswerNormalizationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class FillBlankAndChapterTopicTest {

    @Autowired
    private IQuestionBankService questionBankService;

    @Autowired
    private IExamService examService;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassStaffRepository classStaffRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IExamAnswerRepository examAnswerRepository;

    @Autowired
    private IUserRepository userRepository;

    private User adminUser;
    private User studentUser;
    private Class targetClass;
    private Subject subject;
    private Chapter chapter;
    private Topic topicUnderChapter;
    private Topic topicWithoutChapter;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin");
        assertNotNull(adminUser, "admin user must exist");

        studentUser = userRepository.findByUsername("sv_an");
        assertNotNull(studentUser, "student user must exist");

        targetClass = classRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class must exist in seeded test database"));

        subject = subjectRepository.findById(targetClass.getSubjectId())
                .orElseThrow(() -> new IllegalStateException("Subject must exist for class"));

        chapter = chapterRepository.save(Chapter.builder()
                .subjectId(subject.getSubjectId())
                .chapterCode("CH_" + UUID.randomUUID().toString().substring(0, 8))
                .chapterName("Cơ học động lực học")
                .orderIndex(1)
                .isActive(true)
                .build());

        topicUnderChapter = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .chapterId(chapter.getChapterId())
                .topicName("Định luật II Newton " + UUID.randomUUID().toString().substring(0, 6))
                .orderIndex(1)
                .build());

        topicWithoutChapter = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .chapterId(null)
                .topicName("Chủ đề bổ trợ chưa phân chương " + UUID.randomUUID().toString().substring(0, 6))
                .orderIndex(2)
                .build());

        if (!classEnrollmentRepository.isStudentActivelyEnrolledInClass(targetClass.getClassId(), studentUser.getUserId())) {
            classEnrollmentRepository.save(com.vatly1.example.entity.ClassEnrollment.builder()
                    .classId(targetClass.getClassId())
                    .studentId(studentUser.getUserId())
                    .status(EnrollmentStatus.ACTIVE)
                    .enrolledAt(Instant.now())
                    .build());
        }
    }

    @Test
    @DisplayName("1. Text Normalization: NFC Unicode, case-insensitivity, trim và whitespace compaction")
    void testTextNormalizationAndMatching() {
        // Test Unicode NFC normalization: decomposed vs composed
        String composed = "vận tốc";
        String decomposed = Normalizer.normalize("vận tốc", Normalizer.Form.NFD);
        assertNotEquals(composed, decomposed, "NFD string must be different byte representation");
        assertEquals(AnswerNormalizationUtil.normalize(composed), AnswerNormalizationUtil.normalize(decomposed));

        // Test whitespace trimming & collapse
        assertEquals("newton", AnswerNormalizationUtil.normalize("   Newton   "));
        assertEquals("n", AnswerNormalizationUtil.normalize("  N "));
        assertEquals("gia tốc trọng trường", AnswerNormalizationUtil.normalize("  Gia   tốc    trọng   trường  "));

        // Test matching
        assertTrue(AnswerNormalizationUtil.matches("Newton", "newton"));
        assertTrue(AnswerNormalizationUtil.matches("   N   ", "n"));
        assertTrue(AnswerNormalizationUtil.matches(decomposed, composed));
        assertFalse(AnswerNormalizationUtil.matches("Joule", "newton"));
        assertFalse(AnswerNormalizationUtil.matches("", "newton"));
        assertFalse(AnswerNormalizationUtil.matches(null, "newton"));

        // Match any
        List<String> accepted = Arrays.asList("Newton", "N", "niuton");
        assertTrue(AnswerNormalizationUtil.isMatchAny("newton", accepted));
        assertTrue(AnswerNormalizationUtil.isMatchAny(" N ", accepted));
        assertTrue(AnswerNormalizationUtil.isMatchAny("NIUTON", accepted));
        assertFalse(AnswerNormalizationUtil.isMatchAny("watt", accepted));
    }

    @Test
    @DisplayName("2. Tạo câu hỏi FILL_BLANK với nhiều đáp án hợp lệ thành công")
    void testCreateFillBlankQuestion_Success() {
        CreateQuestionDTO dto = CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị của lực trong hệ SI là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(Arrays.asList("Newton", "N", "Niutơn"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build();

        QuestionBankDTO created = questionBankService.createQuestion(dto, adminUser.getUserId(), "ADMIN");
        assertNotNull(created.getQuestionId());
        assertEquals(QuestionType.FILL_BLANK, created.getQuestionType());
        assertEquals(ApprovalStatus.PENDING, created.getApprovalStatus());
        assertEquals(chapter.getChapterId(), created.getChapterId(), "ChapterId must be inferred from Topic");
        assertTrue(created.getOptions().isEmpty(), "FILL_BLANK must have empty options");
        assertEquals(3, created.getAcceptedAnswers().size());
        assertTrue(created.getAcceptedAnswers().contains("Newton"));
        assertTrue(created.getAcceptedAnswers().contains("N"));

        List<QuestionAcceptedAnswer> savedInDb = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(created.getQuestionId());
        assertEquals(3, savedInDb.size());
    }

    @Test
    @DisplayName("3. Tạo câu hỏi FILL_BLANK thất bại nếu không có đáp án được chấp nhận")
    void testCreateFillBlankQuestion_RequiresAcceptedAnswer() {
        CreateQuestionDTO dto = CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị của gia tốc là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(Collections.emptyList())
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.createQuestion(dto, adminUser.getUserId(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("phải có ít nhất 1 đáp án"));
    }

    @Test
    @DisplayName("4. Validate Topic phải thuộc đúng Subject")
    void testCreateQuestion_ValidatesTopicBelongsToSubject() {
        Subject otherSubject = subjectRepository.save(Subject.builder()
                .subjectCode("OTHER_" + UUID.randomUUID().toString().substring(0, 8))
                .subjectName("Other Subject")
                .isActive(true)
                .build());

        CreateQuestionDTO dto = CreateQuestionDTO.builder()
                .subjectId(otherSubject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId()) // Topic belongs to subject, not otherSubject
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi sai môn học")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .acceptedAnswers(List.of("Đáp án"))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.createQuestion(dto, adminUser.getUserId(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Topic does not belong to the selected subject"));
    }

    @Test
    @DisplayName("5. Hỗ trợ Topic chưa được phân vào Chapter (chapterId nullable)")
    void testTopicWithoutChapter_Supported() {
        CreateQuestionDTO dto = CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicWithoutChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi trên chủ đề độc lập chưa vào chương")
                .difficultyLevel(DifficultyLevel.HARD)
                .acceptedAnswers(List.of("Hợp lệ"))
                .build();

        QuestionBankDTO created = questionBankService.createQuestion(dto, adminUser.getUserId(), "ADMIN");
        assertNotNull(created.getQuestionId());
        assertNull(created.getChapterId(), "ChapterId must be null for topic without chapter");
    }

    @Test
    @DisplayName("6. Truy vấn câu hỏi theo Chapter và Topic")
    void testQueryQuestionsByChapterAndTopic() {
        QuestionBankDTO q1 = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi 1 trong chương")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("Ans1"))
                .build(), adminUser.getUserId(), "ADMIN");

        QuestionBankDTO q2 = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicWithoutChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi 2 không trong chương")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("Ans2"))
                .build(), adminUser.getUserId(), "ADMIN");

        // Query by chapter
        Page<QuestionBankDTO> pageByChapter = questionBankService.getQuestions(
                subject.getSubjectId(), chapter.getChapterId(), null, null, PageRequest.of(0, 10));
        assertTrue(pageByChapter.getContent().stream().anyMatch(q -> q.getQuestionId().equals(q1.getQuestionId())));
        assertFalse(pageByChapter.getContent().stream().anyMatch(q -> q.getQuestionId().equals(q2.getQuestionId())));

        // Query by chapter + topic
        Page<QuestionBankDTO> pageByBoth = questionBankService.getQuestions(
                subject.getSubjectId(), chapter.getChapterId(), topicUnderChapter.getTopicId(), null, PageRequest.of(0, 10));
        assertTrue(pageByBoth.getContent().stream().anyMatch(q -> q.getQuestionId().equals(q1.getQuestionId())));

        // Query by mismatching chapter and topic -> empty
        Page<QuestionBankDTO> pageMismatch = questionBankService.getQuestions(
                subject.getSubjectId(), chapter.getChapterId(), topicWithoutChapter.getTopicId(), null, PageRequest.of(0, 10));
        assertTrue(pageMismatch.getContent().isEmpty());
    }

    @Test
    @DisplayName("7. Cập nhật câu hỏi FILL_BLANK khi chưa thi và bảo vệ lịch sử khi đã thi")
    void testUpdateAndHistoryProtection() {
        CreateQuestionDTO createDTO = CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi ban đầu")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .acceptedAnswers(List.of("Ans1"))
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();
        QuestionBankDTO created = questionBankService.createQuestion(createDTO, adminUser.getUserId(), "ADMIN");

        // 7a. Cập nhật thành công khi chưa dùng trong đề thi
        CreateQuestionDTO updateDTO = CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi đã cập nhật nội dung")
                .difficultyLevel(DifficultyLevel.HARD)
                .acceptedAnswers(Arrays.asList("Ans1_Updated", "Ans2_New"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build();
        QuestionBankDTO updated = questionBankService.updateQuestion(created.getQuestionId(), updateDTO, adminUser.getUserId(), "ADMIN");
        assertEquals("Câu hỏi đã cập nhật nội dung", updated.getContent());
        assertEquals(DifficultyLevel.HARD, updated.getDifficultyLevel());
        assertEquals(2, updated.getAcceptedAnswers().size());

        // 7b. Giả lập câu hỏi được thêm vào đề thi
        Exam exam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Đề thi bảo vệ lịch sử")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());
        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(created.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(2.0))
                .build());

        // Cố gắng cập nhật -> phải bị chặn vì đã sử dụng trong exam
        CustomException updateEx = assertThrows(CustomException.class, () ->
                questionBankService.updateQuestion(created.getQuestionId(), updateDTO, adminUser.getUserId(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, updateEx.getHttpStatus());
        assertTrue(updateEx.getMessage().contains("Cannot update question that has already been used in an exam"));

        // Cố gắng xóa -> phải bị chặn
        CustomException deleteEx = assertThrows(CustomException.class, () ->
                questionBankService.deleteQuestion(created.getQuestionId(), adminUser.getUserId(), "ADMIN"));
        assertEquals(HttpStatus.BAD_REQUEST, deleteEx.getHttpStatus());
        assertTrue(deleteEx.getMessage().contains("Cannot delete question that has already been used in an exam"));
    }

    @Test
    @DisplayName("8. Bảo mật đáp án: Học sinh làm bài thi KHÔNG bị lộ đáp án đúng")
    void testStudentCannotSeeAcceptedAnswersDuringExam() {
        QuestionBankDTO qDTO = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Gia tốc trọng trường tại bề mặt Trái Đất xấp xỉ ____ m/s^2.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(Arrays.asList("9.8", "9.81", "10"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(qDTO.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt câu hỏi");

        Exam exam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Bài kiểm tra bí mật " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(40, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(qDTO.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(2.5))
                .build());

        // Sinh viên bắt đầu làm bài
        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), studentUser.getUserId());
        assertEquals(AttemptStatus.IN_PROGRESS, attempt.getStatus());

        // Lấy danh sách câu hỏi trong lượt làm bài
        ExamAttemptDTO detail = examService.getAttemptDetail(attempt.getAttemptId(), studentUser.getUserId(), "STUDENT");
        List<StudentExamQuestionDTO> questions = detail.getQuestions();
        assertNotNull(questions);
        assertEquals(1, questions.size());

        StudentExamQuestionDTO studentQ = questions.get(0);
        assertEquals(QuestionType.FILL_BLANK, studentQ.getQuestionType());
        // Bảo mật: acceptedAnswers phải là NULL
        assertNull(studentQ.getAcceptedAnswers(), "CRITICAL: Student must NOT see accepted answers during exam!");
        assertTrue(studentQ.getOptions().isEmpty(), "FILL_BLANK must have no MCQ options for student");
        assertNull(studentQ.getIsCorrect(), "isCorrect must be null while IN_PROGRESS");
        assertNull(studentQ.getScore(), "score must be null while IN_PROGRESS");
    }

    @Test
    @DisplayName("9. Chấm bài FILL_BLANK và MCQ: Chấm đúng, chấm sai, chuẩn hóa và tính điểm")
    void testExamGradingFlow_FillBlankAndMcq() {
        // 1. Tạo 1 câu FILL_BLANK (scoreWeight = 3.0)
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị của lực trong hệ SI là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(Arrays.asList("Newton", "N"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(fillQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        // 2. Tạo 1 câu MCQ (scoreWeight = 2.0)
        CreateQuestionDTO.CreateQuestionOptionDTO optA = CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                .content("m/s^2").isCorrect(true).orderIndex(1).build();
        CreateQuestionDTO.CreateQuestionOptionDTO optB = CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                .content("m/s").isCorrect(false).orderIndex(2).build();
        QuestionBankDTO mcqQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Đơn vị của gia tốc là:")
                .difficultyLevel(DifficultyLevel.EASY)
                .options(Arrays.asList(optA, optB))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(mcqQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        UUID correctOptId = mcqQ.getOptions().stream().filter(QuestionOptionDTO::getIsCorrect).findFirst().get().getOptionId();

        // 3. Tạo Exam
        Exam exam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Bài kiểm tra tổng hợp " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(40, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(3.0))
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(mcqQ.getQuestionId())
                .orderIndex(2)
                .scoreWeight(BigDecimal.valueOf(2.0))
                .build());

        // Bắt đầu làm bài
        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), studentUser.getUserId());

        // Sinh viên nộp câu trả lời FILL_BLANK với khoảng trắng và viết thường: "  newton   "
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .answerText("   newton   ")
                .build(), studentUser.getUserId());

        // Sinh viên nộp câu trả lời MCQ đúng
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(mcqQ.getQuestionId())
                .selectedOptionIds(List.of(correctOptId))
                .build(), studentUser.getUserId());

        // Nộp bài thi và chấm điểm
        ExamAttemptDTO gradedAttempt = examService.submitAttempt(attempt.getAttemptId(), studentUser.getUserId());
        assertEquals(AttemptStatus.GRADED, gradedAttempt.getStatus());
        // Điểm mong đợi: 3.0 + 2.0 = 5.0
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(gradedAttempt.getTotalScore()),
                "Total score must be 5.0 (3.0 for fill + 2.0 for MCQ)");

        // Kiểm tra chi tiết đáp án trong DB
        ExamAnswer fillAns = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), fillQ.getQuestionId()).orElseThrow();
        assertTrue(fillAns.getIsCorrect());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(fillAns.getScore()));
        assertEquals("   newton   ", fillAns.getAnswerText());

        ExamAnswer mcqAns = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), mcqQ.getQuestionId()).orElseThrow();
        assertTrue(mcqAns.getIsCorrect());
        assertEquals(0, BigDecimal.valueOf(2.0).compareTo(mcqAns.getScore()));

        // Kiểm tra sau khi chấm, học sinh được phép xem đáp án và Topic/Chapter
        ExamAttemptDTO postGrading = examService.getAttemptDetail(attempt.getAttemptId(), studentUser.getUserId(), "STUDENT");
        StudentExamQuestionDTO postQ = postGrading.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(fillQ.getQuestionId()))
                .findFirst().orElseThrow();
        assertTrue(postQ.getIsCorrect());
        assertEquals(chapter.getChapterId(), postQ.getChapterId(), "Chapter must be inferred on StudentExamQuestionDTO");
        assertEquals("Cơ học động lực học", postQ.getChapterName());
        assertNotNull(postQ.getAcceptedAnswers(), "Accepted answers must be visible after grading for practice exam");
        assertTrue(postQ.getAcceptedAnswers().contains("Newton"));
    }

    @Test
    @DisplayName("10. Chấm sai FILL_BLANK khi học sinh nhập sai hoặc để trống")
    void testExamGradingFlow_FillBlankIncorrectAndBlank() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topicUnderChapter.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Công thức vận tốc v = v0 + ____.")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .acceptedAnswers(List.of("at"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(fillQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        Exam exam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Đề thi chấm sai " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(25, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(2.0))
                .build());

        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), studentUser.getUserId());

        // Sinh viên trả lời sai kiến thức: "gt" thay vì "at"
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .answerText("gt")
                .build(), studentUser.getUserId());

        ExamAttemptDTO graded = examService.submitAttempt(attempt.getAttemptId(), studentUser.getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(graded.getTotalScore()));

        ExamAnswer ans = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), fillQ.getQuestionId()).orElseThrow();
        assertFalse(ans.getIsCorrect());
        assertEquals(0, BigDecimal.ZERO.compareTo(ans.getScore()));
    }

    @Test
    @DisplayName("11. Phục vụ ôn tập: Truy xuất câu hỏi làm sai theo Topic và Chapter, và lấy câu APPROVED")
    void testRemediationQueriesSupport() {
        List<QuestionBankDTO> approvedList = questionBankService.getApprovedQuestionsBySubjectAndTopic(
                subject.getSubjectId(), topicUnderChapter.getTopicId());
        assertNotNull(approvedList);
        for (QuestionBankDTO q : approvedList) {
            assertEquals(ApprovalStatus.APPROVED, q.getApprovalStatus());
            assertEquals(topicUnderChapter.getTopicId(), q.getTopicId());
            assertEquals(chapter.getChapterId(), q.getChapterId(), "Inferred chapter must match");
        }
    }
}
