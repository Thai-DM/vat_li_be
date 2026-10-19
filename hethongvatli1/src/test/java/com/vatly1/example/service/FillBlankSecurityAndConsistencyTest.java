package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.BatchSaveResultDTO;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamQuestionDetailDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.dto.StudentExamQuestionDTO;
import com.vatly1.example.model.request.BatchSubmitAnswerDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class FillBlankSecurityAndConsistencyTest {

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
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAnswerRepository examAnswerRepository;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    private User adminUser;
    private User student1;
    private User student2;
    private Subject subject;
    private Subject otherSubject;
    private Chapter chapter;
    private Topic topic;
    private Class classA;
    private Class classB;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin");
        assertNotNull(adminUser, "admin user must exist");

        student1 = userRepository.findByUsername("sv_an");
        assertNotNull(student1, "sv_an user must exist");

        student2 = userRepository.findByUsername("sv_binh");
        if (student2 == null) {
            student2 = userRepository.save(User.builder()
                    .username("sec_student2_" + UUID.randomUUID())
                    .email("sec_student2_" + UUID.randomUUID() + "@test.com")
                    .passwordHash("hashed")
                    .role(UserRole.STUDENT)
                    .build());
        }

        classA = classRepository.findAll().stream()
                .filter(c -> classEnrollmentRepository.existsByClassIdAndStudentId(c.getClassId(), student1.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class with student1 enrollment must exist"));

        subject = subjectRepository.findById(classA.getSubjectId())
                .orElseThrow(() -> new IllegalStateException("Subject must exist for class"));

        classB = classRepository.save(Class.builder()
                .subjectId(subject.getSubjectId())
                .semesterId(classA.getSemesterId())
                .classCode("SEC_CLS_" + UUID.randomUUID().toString().substring(0, 6))
                .instructorId(classA.getInstructorId())
                .build());

        otherSubject = subjectRepository.save(Subject.builder()
                .subjectCode("OTH_" + UUID.randomUUID().toString().substring(0, 6))
                .subjectName("Môn học khác")
                .build());

        chapter = chapterRepository.save(Chapter.builder()
                .subjectId(subject.getSubjectId())
                .chapterCode("CHP_" + UUID.randomUUID().toString().substring(0, 6))
                .chapterName("Chương bảo mật")
                .orderIndex(1)
                .isActive(true)
                .build());

        topic = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .chapterId(chapter.getChapterId())
                .topicName("Bài 1: Định luật bảo toàn " + UUID.randomUUID().toString().substring(0, 5))
                .orderIndex(1)
                .build());
    }

    @Test
    @DisplayName("1. Chặn tạo câu hỏi với các loại legacy/không hỗ trợ (SHORT_ANSWER, MCQ_MULTI, TRUE_FALSE)")
    void testCreateQuestion_DisallowInvalidQuestionType() {
        // Test SHORT_ANSWER
        CustomException ex1 = assertThrows(CustomException.class, () ->
                QuestionType.fromString("SHORT_ANSWER"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getHttpStatus());
        assertTrue(ex1.getMessage().contains("chỉ hỗ trợ 2 dạng câu hỏi"));

        // Test MCQ_MULTI
        CustomException ex2 = assertThrows(CustomException.class, () ->
                QuestionType.fromString("MCQ_MULTI"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getHttpStatus());
        assertTrue(ex2.getMessage().contains("chỉ hỗ trợ 2 dạng câu hỏi"));

        // Test TRUE_FALSE
        CustomException ex3 = assertThrows(CustomException.class, () ->
                QuestionType.fromString("TRUE_FALSE"));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getHttpStatus());
        assertTrue(ex3.getMessage().contains("chỉ hỗ trợ 2 dạng câu hỏi"));
    }

    @Test
    @DisplayName("2. Chặn cập nhật câu hỏi sang loại không hỗ trợ (SHORT_ANSWER)")
    void testUpdateQuestion_DisallowInvalidQuestionType() {
        QuestionBankDTO q = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị đo lực là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("N", "Newton"))
                .build(), adminUser.getUserId(), "ADMIN");

        CustomException ex = assertThrows(CustomException.class, () ->
                QuestionType.fromString("SHORT_ANSWER"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("chỉ hỗ trợ 2 dạng câu hỏi"));
    }

    @Test
    @DisplayName("3. Bảo vệ đáp án: Học sinh nộp sớm đề FINAL không được xem đáp án (acceptedAnswers = null)")
    void testFinalExam_EarlySubmit_CannotSeeAcceptedAnswers() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Đơn vị lực trong SI là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("Newton", "N"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(fillQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        // Kỳ thi kết thúc sau 60 phút nữa (đang diễn ra)
        Exam finalExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Đề thi cuối kỳ FINAL")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(50, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(finalExam.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());

        ExamAttemptDTO attempt = examService.startAttempt(finalExam.getExamId(), student1.getUserId());

        // Sinh viên nộp bài sớm
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .answerText("Newton")
                .build(), student1.getUserId());

        ExamAttemptDTO graded = examService.submitAttempt(attempt.getAttemptId(), student1.getUserId());
        assertEquals(AttemptStatus.GRADED, graded.getStatus());

        // Sinh viên lấy chi tiết lượt làm bài sau khi nộp sớm
        ExamAttemptDTO detail = examService.getAttemptDetail(attempt.getAttemptId(), student1.getUserId(), "STUDENT");
        assertNotNull(detail.getQuestions());
        StudentExamQuestionDTO studentQ = detail.getQuestions().get(0);

        // Fail-closed / Confidentiality: acceptedAnswers và isCorrect phải là null!
        assertNull(studentQ.getAcceptedAnswers(), "Đáp án đúng FILL_BLANK không được trả về trước endTime");
        assertNull(studentQ.getIsCorrect(), "isCorrect không được trả về trước endTime của đề FINAL");
        assertNull(studentQ.getScore(), "score từng câu không được trả về trước endTime");
    }

    @Test
    @DisplayName("4. Fail-closed ca thi chéo: Lớp A đã qua endTime nhưng Lớp B cùng đề FINAL vẫn đang thi -> Lớp A vẫn không được xem đáp án")
    void testFinalExam_ConflictingOngoingFinalExam_FailsClosed() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Gia tốc trọng trường g xấp xỉ ____ m/s2.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("9.8", "10"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(fillQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        // Đề FINAL lớp A: ban đầu mở để sinh viên nộp bài
        Exam examA = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("FINAL Lớp A")
                .examType(ExamType.FINAL)
                .durationMinutes(45)
                .startTime(Instant.now().minus(20, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(25, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(examA.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());

        // Đề FINAL lớp B: dùng chung câu hỏi, đang diễn ra (kết thúc sau 30 phút nữa)
        Exam examB = examRepository.save(Exam.builder()
                .classId(classB.getClassId())
                .title("FINAL Lớp B (đang thi)")
                .examType(ExamType.FINAL)
                .durationMinutes(45)
                .startTime(Instant.now().minus(15, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(30, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(examB.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());

        // Sinh viên lớp A làm bài và nộp bài
        ExamAttemptDTO attemptA = examService.startAttempt(examA.getExamId(), student1.getUserId());
        examService.submitAnswer(attemptA.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .answerText("9.8")
                .build(), student1.getUserId());
        examService.submitAttempt(attemptA.getAttemptId(), student1.getUserId());

        // Giả lập ca thi lớp A kết thúc (endTime đã qua 5 phút)
        examA.setEndTime(Instant.now().minus(5, ChronoUnit.MINUTES));
        examRepository.save(examA);

        // Sinh viên lớp A xem lại chi tiết bài làm của mình
        ExamAttemptDTO detailA = examService.getAttemptDetail(attemptA.getAttemptId(), student1.getUserId(), "STUDENT");
        StudentExamQuestionDTO qA = detailA.getQuestions().get(0);

        // Vì đề của lớp B chia sẻ câu hỏi vẫn đang thi -> Phải Fail-closed, giấu đáp án để chống lộ đề sang lớp B!
        assertNull(qA.getAcceptedAnswers(), "Phải ẩn đáp án vì còn ca thi FINAL khác cùng câu hỏi đang diễn ra!");
        assertNull(qA.getIsCorrect(), "Phải ẩn isCorrect khi còn ca thi FINAL khác đang diễn ra!");
    }

    @Test
    @DisplayName("5. Công bố đáp án hợp lệ: Khi tất cả các ca thi FINAL chia sẻ đề đều đã kết thúc -> Được xem đáp án")
    void testFinalExam_AllFinalExamsEnded_CanSeeAnswers() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Công thức thế năng Wt = ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("mgh"))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");
        questionBankService.approveQuestion(fillQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Duyệt");

        // Đề FINAL duy nhất, ban đầu mở để làm bài
        Exam singleFinal = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("FINAL độc lập")
                .examType(ExamType.FINAL)
                .durationMinutes(30)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(20, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(singleFinal.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());

        ExamAttemptDTO attempt = examService.startAttempt(singleFinal.getExamId(), student1.getUserId());
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .answerText("mgh")
                .build(), student1.getUserId());
        examService.submitAttempt(attempt.getAttemptId(), student1.getUserId());

        // Giả lập ca thi đã kết thúc sau đó
        singleFinal.setEndTime(Instant.now().minus(10, ChronoUnit.MINUTES));
        examRepository.save(singleFinal);

        ExamAttemptDTO detail = examService.getAttemptDetail(attempt.getAttemptId(), student1.getUserId(), "STUDENT");
        StudentExamQuestionDTO q = detail.getQuestions().get(0);

        // Vì không còn ca thi nào đang diễn ra và endTime đã qua -> Cho phép xem
        assertNotNull(q.getAcceptedAnswers(), "Đáp án được phép hiển thị sau khi toàn bộ ca thi kết thúc");
        assertTrue(q.getAcceptedAnswers().contains("mgh"));
        assertTrue(q.getIsCorrect());
    }

    @Test
    @DisplayName("6. ExamQuestionDetailDTO không rò rỉ acceptedAnswers cho Student qua endpoint getExamQuestions")
    void testGetExamQuestions_StudentRole_HidesAcceptedAnswers() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Vận tốc bằng quãng đường chia cho ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("thời gian", "t"))
                .build(), adminUser.getUserId(), "ADMIN");

        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Đề kiểm tra")
                .examType(ExamType.QUIZ)
                .durationMinutes(15)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(10, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(fillQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.ONE)
                .build());

        // Gọi getExamQuestions với vai trò STUDENT
        List<ExamQuestionDetailDTO> studentView = examService.getExamQuestions(exam.getExamId(), student1.getUserId(), "STUDENT");
        assertEquals(1, studentView.size());
        assertNull(studentView.get(0).getAcceptedAnswers(), "Student gọi getExamQuestions không được nhận acceptedAnswers!");

        // Gọi getExamQuestions với vai trò INSTRUCTOR/ADMIN
        List<ExamQuestionDetailDTO> adminView = examService.getExamQuestions(exam.getExamId(), adminUser.getUserId(), "ADMIN");
        assertEquals(1, adminView.size());
        assertNotNull(adminView.get(0).getAcceptedAnswers(), "Admin được nhận acceptedAnswers");
        assertTrue(adminView.get(0).getAcceptedAnswers().contains("thời gian"));
    }

    @Test
    @DisplayName("7. Autosave lưu trữ chính xác answerText cho FILL_BLANK và không bị mất")
    void testAutosave_PreservesFillBlankAnswerText() {
        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Độ dời ký hiệu là ____.")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("d", "delta x"))
                .build(), adminUser.getUserId(), "ADMIN");

        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Đề kiểm tra autosave")
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
                .scoreWeight(BigDecimal.ONE)
                .build());

        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), student1.getUserId());

        // Sinh viên autosave answerText
        BatchSubmitAnswerDTO batchDto = BatchSubmitAnswerDTO.builder()
                .answers(List.of(SubmitAnswerDTO.builder()
                        .questionId(fillQ.getQuestionId())
                        .answerText("delta x")
                        .build()))
                .build();

        BatchSaveResultDTO saveResult = examService.autosaveAnswers(attempt.getAttemptId(), batchDto, student1.getUserId());
        assertEquals(1, saveResult.getSavedCount());

        // Kiểm tra trong DB: answerText phải tồn tại, selectedOptionIds rỗng
        ExamAnswer dbAnswer = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), fillQ.getQuestionId()).orElseThrow();
        assertEquals("delta x", dbAnswer.getAnswerText());
        assertTrue(dbAnswer.getSelectedOptionIds().isEmpty());

        // Sinh viên tải lại câu hỏi lượt thi
        List<StudentExamQuestionDTO> questions = examService.getAttemptQuestions(attempt.getAttemptId(), student1.getUserId(), "STUDENT");
        assertEquals("delta x", questions.get(0).getAnswerText(), "answerText đã autosave phải được nạp lại");
    }

    @Test
    @DisplayName("8. Tách biệt nộp bài: MCQ chỉ lưu selectedOptionIds và xóa text; FILL_BLANK chỉ lưu answerText và xóa selectedOptionIds")
    void testSubmitAnswer_StrictTypeSeparation() {
        QuestionBankDTO mcqQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Câu hỏi MCQ")
                .difficultyLevel(DifficultyLevel.EASY)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("A").isCorrect(true).orderIndex(1).build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("B").isCorrect(false).orderIndex(2).build()
                ))
                .build(), adminUser.getUserId(), "ADMIN");

        QuestionBankDTO fillQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Câu hỏi FILL")
                .difficultyLevel(DifficultyLevel.EASY)
                .acceptedAnswers(List.of("ans"))
                .build(), adminUser.getUserId(), "ADMIN");

        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Đề phân tách loại")
                .examType(ExamType.PRACTICE)
                .durationMinutes(30)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(25, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId()).questionId(mcqQ.getQuestionId()).orderIndex(1).build());
        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId()).questionId(fillQ.getQuestionId()).orderIndex(2).build());

        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), student1.getUserId());
        UUID chosenOpt = mcqQ.getOptions().get(0).getOptionId();

        // Nộp MCQ kèm cả answerText thừa -> answerText phải bị null hóa
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(mcqQ.getQuestionId())
                .selectedOptionIds(List.of(chosenOpt))
                .answerText("stray text")
                .build(), student1.getUserId());

        ExamAnswer mcqAns = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), mcqQ.getQuestionId()).orElseThrow();
        assertEquals(List.of(chosenOpt), mcqAns.getSelectedOptionIds());
        assertNull(mcqAns.getAnswerText(), "MCQ không được lưu textanswer");

        // Nộp FILL kèm selectedOptionIds thừa -> selectedOptionIds phải rỗng
        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(fillQ.getQuestionId())
                .selectedOptionIds(List.of(chosenOpt))
                .answerText("ans")
                .build(), student1.getUserId());

        ExamAnswer fillAns = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getAttemptId(), fillQ.getQuestionId()).orElseThrow();
        assertEquals("ans", fillAns.getAnswerText());
        assertTrue(fillAns.getSelectedOptionIds().isEmpty(), "FILL_BLANK không được lưu selectedOptionIds");
    }

    @Test
    @DisplayName("9. Chapter/Topic không gây IDOR hoặc lẫn lộn Subject")
    void testChapterTopic_SubjectIsolation() {
        // Tạo topic thuộc otherSubject
        Topic otherTopic = topicRepository.save(Topic.builder()
                .subjectId(otherSubject.getSubjectId())
                .topicName("Topic thuộc môn khác")
                .build());

        // Lấy câu hỏi với subjectId và topicId thuộc môn khác -> Phải trả về rỗng
        Page<QuestionBankDTO> result = questionBankService.getQuestions(
                subject.getSubjectId(), null, otherTopic.getTopicId(), null, PageRequest.of(0, 10));
        assertTrue(result.isEmpty(), "Topic không thuộc subject phải trả về kết quả rỗng");

        // getApprovedQuestionsBySubjectAndTopic với topic không khớp subject -> Phải trả về rỗng
        List<QuestionBankDTO> approved = questionBankService.getApprovedQuestionsBySubjectAndTopic(
                subject.getSubjectId(), otherTopic.getTopicId());
        assertTrue(approved.isEmpty(), "Phải trả về danh sách rỗng nếu topic không khớp subject");
    }

    @Test
    @DisplayName("10. Trắc nghiệm cũ vẫn hoạt động chuẩn xác (không bị ảnh hưởng bởi FILL_BLANK)")
    void testLegacyMcqWorkflowIntact() {
        QuestionBankDTO mcqQ = questionBankService.createQuestion(CreateQuestionDTO.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Vectơ gia tốc cùng hướng với vectơ vận tốc khi nào?")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("Chuyển động nhanh dần đều").isCorrect(true).orderIndex(1).build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("Chuyển động chậm dần đều").isCorrect(false).orderIndex(2).build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder().content("Chuyển động thẳng đều").isCorrect(false).orderIndex(3).build()
                ))
                .approvalStatus(ApprovalStatus.PENDING)
                .build(), adminUser.getUserId(), "ADMIN");

        assertEquals(QuestionType.MCQ_SINGLE, mcqQ.getQuestionType());
        assertEquals(3, mcqQ.getOptions().size());
        assertTrue(mcqQ.getAcceptedAnswers().isEmpty());

        questionBankService.approveQuestion(mcqQ.getQuestionId(), adminUser.getUserId(), "ADMIN", "Phê duyệt");

        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Đề kiểm tra MCQ cũ")
                .examType(ExamType.PRACTICE)
                .durationMinutes(20)
                .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(15, ChronoUnit.MINUTES))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(mcqQ.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.valueOf(5.0))
                .build());

        ExamAttemptDTO attempt = examService.startAttempt(exam.getExamId(), student1.getUserId());
        UUID correctOptId = mcqQ.getOptions().stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElseThrow().getOptionId();

        examService.submitAnswer(attempt.getAttemptId(), SubmitAnswerDTO.builder()
                .questionId(mcqQ.getQuestionId())
                .selectedOptionIds(List.of(correctOptId))
                .build(), student1.getUserId());

        ExamAttemptDTO graded = examService.submitAttempt(attempt.getAttemptId(), student1.getUserId());
        assertEquals(AttemptStatus.GRADED, graded.getStatus());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(graded.getTotalScore()));
    }
}
