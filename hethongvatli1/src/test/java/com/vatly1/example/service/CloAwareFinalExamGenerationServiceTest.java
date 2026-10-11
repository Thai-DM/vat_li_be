package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.response.MatrixValidationResultDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class CloAwareFinalExamGenerationServiceTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamMatrixDetailRepository examMatrixDetailRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    private User instructor;
    private User student;
    private Subject targetSubject;
    private Subject otherSubject;
    private Class targetClass;
    private Class secondClass;
    private Topic topic1;
    private Topic topic2;
    private Clo clo1;
    private Clo clo2;

    @BeforeEach
    void setUp() {
        instructor = userRepository.findByUsername("gv_nguyen");
        assertNotNull(instructor, "Instructor gv_nguyen must exist");

        student = userRepository.findByUsername("sv_an");
        assertNotNull(student, "Student sv_an must exist");

        targetSubject = subjectRepository.save(Subject.builder()
                .subjectCode("SUB_FINAL_" + UUID.randomUUID().toString().substring(0, 8))
                .subjectName("Subject Final Test " + UUID.randomUUID())
                .description("Test Subject for Final Exam Generation")
                .isActive(true)
                .build());

        otherSubject = subjectRepository.save(Subject.builder()
                .subjectCode("SUB_OTHER_" + UUID.randomUUID().toString().substring(0, 8))
                .subjectName("Other Subject " + UUID.randomUUID())
                .description("Other Subject")
                .isActive(true)
                .build());

        targetClass = classRepository.save(Class.builder()
                .subjectId(targetSubject.getSubjectId())
                .instructorId(instructor.getUserId())
                .classCode("CLS_" + UUID.randomUUID().toString().substring(0, 8))
                .status(com.vatly1.example.entity.enums.ClassStatus.ACTIVE)
                .build());

        secondClass = classRepository.save(Class.builder()
                .subjectId(targetSubject.getSubjectId())
                .instructorId(instructor.getUserId())
                .classCode("CLS2_" + UUID.randomUUID().toString().substring(0, 8))
                .status(com.vatly1.example.entity.enums.ClassStatus.ACTIVE)
                .build());

        topic1 = topicRepository.save(Topic.builder()
                .subjectId(targetSubject.getSubjectId())
                .topicName("Topic 1 " + UUID.randomUUID())
                .orderIndex(1)
                .build());

        topic2 = topicRepository.save(Topic.builder()
                .subjectId(targetSubject.getSubjectId())
                .topicName("Topic 2 " + UUID.randomUUID())
                .orderIndex(2)
                .build());

        clo1 = cloRepository.save(Clo.builder()
                .subjectId(targetSubject.getSubjectId())
                .cloCode("CLO1_" + UUID.randomUUID().toString().substring(0, 6))
                .description("Chuẩn đầu ra 1")
                .orderIndex(1)
                .isActive(true)
                .build());

        clo2 = cloRepository.save(Clo.builder()
                .subjectId(targetSubject.getSubjectId())
                .cloCode("CLO2_" + UUID.randomUUID().toString().substring(0, 6))
                .description("Chuẩn đầu ra 2")
                .orderIndex(2)
                .isActive(true)
                .build());
    }

    private QuestionBank createQuestion(Subject subject, Topic topic, DifficultyLevel level, ApprovalStatus status) {
        return questionBankRepository.save(QuestionBank.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .difficultyLevel(level)
                .questionType(QuestionType.MCQ_SINGLE)
                .approvalStatus(status)
                .content("Question " + UUID.randomUUID())
                .createdBy(instructor.getUserId())
                .build());
    }

    private void linkQuestionClo(QuestionBank question, Clo clo) {
        questionCloRepository.save(QuestionClo.builder()
                .questionId(question.getQuestionId())
                .cloId(clo.getCloId())
                .subjectId(question.getSubjectId())
                .build());
    }

    private ExamMatrix createPublishedFinalMatrix(List<ExamMatrixDetail> details) {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(targetSubject.getSubjectId())
                .matrixName("Ma trận Final Chuẩn " + UUID.randomUUID())
                .examType(ExamType.FINAL)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .matrixFamilyId(UUID.randomUUID())
                .revisionNumber(1)
                .rowVersion(0L)
                .build());

        for (ExamMatrixDetail d : details) {
            d.setMatrixId(matrix.getMatrixId());
            d.setSubjectId(targetSubject.getSubjectId());
            if (d.getWeightPercent() == null) {
                d.setWeightPercent(new BigDecimal("1.0"));
            }
            examMatrixDetailRepository.save(d);
        }
        return matrix;
    }

    private Exam createFinalExam(Class clazz, ExamMatrix matrix) {
        return examRepository.save(Exam.builder()
                .classId(clazz.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Đề thi cuối kỳ " + UUID.randomUUID())
                .examType(ExamType.FINAL)
                .durationMinutes(90)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Test 1: FINAL sinh đúng CLO đã cấu hình trong MatrixDetail")
    void test01_finalGeneratesExactCloMatching() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);

        QuestionBank q2 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q2, clo2);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        int count = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(1, count);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(1, questions.size());
        assertEquals(q1.getQuestionId(), questions.get(0).getQuestionId(), "Phải chọn đúng câu hỏi gắn với CLO1");
    }

    @Test
    @DisplayName("Test 2: FINAL sinh đúng DifficultyLevel đã cấu hình trong MatrixDetail")
    void test02_finalGeneratesExactDifficultyMatching() {
        QuestionBank qEasy = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(qEasy, clo1);
        QuestionBank qHard = createQuestion(targetSubject, topic1, DifficultyLevel.HARD, ApprovalStatus.APPROVED);
        linkQuestionClo(qHard, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.HARD).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        int count = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(1, count);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(qHard.getQuestionId(), questions.get(0).getQuestionId(), "Phải chọn đúng câu hỏi có mức độ HARD");
    }

    @Test
    @DisplayName("Test 3: FINAL chỉ lấy câu hỏi APPROVED, loại bỏ hoàn toàn DRAFT/PENDING/REJECTED")
    void test03_finalOnlySelectsApprovedQuestions() {
        QuestionBank qApproved = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(qApproved, clo1);
        QuestionBank qDraft = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.DRAFT);
        linkQuestionClo(qDraft, clo1);
        QuestionBank qPending = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.PENDING);
        linkQuestionClo(qPending, clo1);
        QuestionBank qRejected = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.REJECTED);
        linkQuestionClo(qRejected, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        int count = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(1, count);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(qApproved.getQuestionId(), questions.get(0).getQuestionId());
    }

    @Test
    @DisplayName("Test 4: FINAL không lấy câu hỏi từ môn học khác (Other Subject)")
    void test04_finalDoesNotSelectQuestionsFromOtherSubject() {
        Topic otherTopic = topicRepository.save(Topic.builder().subjectId(otherSubject.getSubjectId()).topicName("Other Topic").orderIndex(1).build());
        QuestionBank qOther = createQuestion(otherSubject, otherTopic, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 5: FINAL không duplicate câu hỏi khi nhiều dòng ma trận cùng tranh chấp ứng viên")
    void test05_finalDoesNotDuplicateQuestions() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);
        linkQuestionClo(q1, clo2);

        QuestionBank q2 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q2, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build(),
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo2.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        int count = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(2, count);

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(2, questions.size());
        assertNotEquals(questions.get(0).getQuestionId(), questions.get(1).getQuestionId(), "Hai câu hỏi trong đề phải khác nhau hoàn toàn");
    }

    @Test
    @DisplayName("Test 6: FINAL thiếu câu hỏi trả lỗi 400 rõ ràng và rollback toàn bộ")
    void test06_finalInsufficientQuestionsFailsWithClearMessage() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);

        // Yêu cầu 2 câu nhưng chỉ có 1 câu
        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(2).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Không thể sinh đề thi cuối kỳ"), "Thông điệp lỗi phải nêu rõ lý do");
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()), "Phải rollback, không để lại câu hỏi dở dang");
    }

    @Test
    @DisplayName("Test 7: FINAL không fallback sang Difficulty khác (Fail-Closed)")
    void test7_finalDoesNotFallbackToDifferentDifficulty() {
        // Có câu EASY nhưng yêu cầu HARD
        QuestionBank qEasy = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(qEasy, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.HARD).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 8: FINAL không bỏ điều kiện CLO khi không đủ câu (Fail-Closed)")
    void test08_finalDoesNotDropCloRequirement() {
        // Có câu đúng Topic + Difficulty nhưng không gắn CLO1
        QuestionBank qNoClo = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 9: MatrixDetail có cloId = null vẫn hoạt động bình thường theo Topic và Difficulty")
    void test09_finalMatrixDetailWithNullCloWorks() {
        QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(null).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        int count = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(1, count);
        assertEquals(1, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 10: Hai Class cùng Matrix tạo hai Exam độc lập với ID riêng biệt")
    void test10_twoClassesSameMatrix_createTwoIndependentExams() {
        for (int i = 0; i < 4; i++) {
            QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            linkQuestionClo(q, clo1);
        }

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(2).build()
        ));

        Exam examClass1 = createFinalExam(targetClass, matrix);
        Exam examClass2 = createFinalExam(secondClass, matrix);

        assertNotEquals(examClass1.getExamId(), examClass2.getExamId(), "Hai bài thi phải có ID độc lập");
        assertEquals(examClass1.getMatrixId(), examClass2.getMatrixId(), "Cả hai bài thi cùng trỏ về một ma trận");
    }

    @Test
    @DisplayName("Test 11: Hai Exam độc lập từ hai Class đều tuân thủ và sinh đủ câu theo ma trận")
    void test11_twoIndependentExamsBothSatisfyMatrixRequirements() {
        for (int i = 0; i < 4; i++) {
            QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            linkQuestionClo(q, clo1);
        }

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(2).build()
        ));

        Exam examClass1 = createFinalExam(targetClass, matrix);
        Exam examClass2 = createFinalExam(secondClass, matrix);

        int count1 = examService.autoGenerateQuestions(examClass1.getExamId(), instructor.getUserId());
        int count2 = examService.autoGenerateQuestions(examClass2.getExamId(), instructor.getUserId());

        assertEquals(2, count1);
        assertEquals(2, count2);

        List<ExamQuestion> qList1 = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(examClass1.getExamId());
        List<ExamQuestion> qList2 = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(examClass2.getExamId());

        assertEquals(2, qList1.size());
        assertEquals(2, qList2.size());
    }

    @Test
    @DisplayName("Test 12: Không cho phép sửa hoặc sinh lại câu hỏi khi bài thi đã có Student Attempt")
    void test12_cannotModifyExamWithAttempts() {
        QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        // Sinh đề lần 1
        examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());

        // Giả lập sinh viên nộp bài làm (attempt)
        examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.SUBMITTED)
                .startedAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .submittedAt(Instant.now())
                .totalScore(BigDecimal.valueOf(10))
                .build());

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("attempts") || ex.getMessage().contains("lượt làm bài"));
    }

    @Test
    @DisplayName("Test 13: Không cho phép sinh đè câu hỏi khi bài thi đã có câu hỏi trước đó")
    void test13_cannotRegenerateExamWithExistingQuestions() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        // Sinh đề lần 1: thành công
        examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(1, examQuestionRepository.countByExamId(exam.getExamId()));

        // Sinh đề lần 2 cho cùng kỳ thi: Bị chặn
        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("đã có danh sách câu hỏi"));
    }

    @Test
    @DisplayName("Test 14: Transaction rollback toàn vẹn khi thiếu câu hỏi")
    void test14_transactionRollbackWhenQuestionsInsufficient() {
        // Detail 1 cần 1 câu (có), Detail 2 cần 1 câu (thiếu)
        QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build(),
                ExamMatrixDetail.builder().topicId(topic2.getTopicId()).difficultyLevel(DifficultyLevel.HARD).cloId(clo2.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });

        // Xác nhận Detail 1 không bị lưu lẻ tẻ
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 15: Ma trận chưa PUBLISHED (DRAFT) bị từ chối sinh đề FINAL")
    void test15_transactionRollbackWhenMatrixNotPublished() {
        QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q, clo1);

        // Tạo ma trận ở trạng thái DRAFT
        ExamMatrix draftMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(targetSubject.getSubjectId())
                .matrixName("Ma trận DRAFT " + UUID.randomUUID())
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .matrixFamilyId(UUID.randomUUID())
                .revisionNumber(1)
                .build());

        examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(draftMatrix.getMatrixId())
                .subjectId(targetSubject.getSubjectId())
                .topicId(topic1.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .cloId(clo1.getCloId())
                .numQuestions(1)
                .weightPercent(BigDecimal.ONE)
                .build());

        Exam exam = createFinalExam(targetClass, draftMatrix);

        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("PUBLISHED"));
        assertEquals(0, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 16: Validation và Generation cho ra cùng kết luận về tính khả thi với cùng snapshot")
    void test16_validationAndGenerationYieldIdenticalConclusions() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);

        // Yêu cầu 2 câu nhưng chỉ có 1 câu
        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(2).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        // 1. Validation báo không khả thi
        MatrixValidationResultDTO validation = examMatrixService.validateMatrixQuestionAvailability(matrix.getMatrixId());
        assertFalse(validation.isValid(), "Validation phải kết luận false");

        // 2. Generation cũng phải ném lỗi thất bại
        assertThrows(CustomException.class, () -> {
            examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        });

        // Thêm câu thứ 2
        QuestionBank q2 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q2, clo1);

        // 3. Giờ validation báo hợp lệ
        MatrixValidationResultDTO validation2 = examMatrixService.validateMatrixQuestionAvailability(matrix.getMatrixId());
        assertTrue(validation2.isValid(), "Validation phải kết luận true sau khi thêm câu");

        // 4. Generation thành công
        int added = examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
        assertEquals(2, added);
    }

    @Test
    @DisplayName("Test 17: Hai request sinh đề đồng thời cho cùng Exam không tạo dữ liệu trùng hoặc sai")
    void test17_concurrentGenerationRequestsSafe() throws Exception {
        for (int i = 0; i < 3; i++) {
            QuestionBank q = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            linkQuestionClo(q, clo1);
        }

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(2).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> task = () -> {
            try {
                return examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());
            } catch (Exception e) {
                return -1; // Request thứ hai bị chặn do pessimistic lock và kiểm tra đã có câu hỏi
            }
        };

        Future<Integer> f1 = executor.submit(task);
        Future<Integer> f2 = executor.submit(task);

        int r1 = f1.get(5, TimeUnit.SECONDS);
        int r2 = f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Một request thành công sinh 2 câu, một request bị chặn trả về -1
        assertTrue((r1 == 2 && r2 == -1) || (r1 == -1 && r2 == 2), "Chỉ đúng 1 request được sinh đề");
        assertEquals(2, examQuestionRepository.countByExamId(exam.getExamId()), "Số câu hỏi được lưu trong DB bắt buộc là 2");
    }

    @Test
    @DisplayName("Test 18: PRACTICE/QUIZ vẫn giữ nguyên cơ chế fallback tương thích ngược")
    void test18_practiceAndQuizRetainBackwardCompatibility() {
        // Có 1 câu HARD, 2 câu EASY. Ma trận PRACTICE yêu cầu 3 câu HARD.
        QuestionBank qHard = createQuestion(targetSubject, topic1, DifficultyLevel.HARD, ApprovalStatus.APPROVED);
        QuestionBank qEasy1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank qEasy2 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        ExamMatrix practiceMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(targetSubject.getSubjectId())
                .matrixName("Ma trận Practice " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .matrixFamilyId(UUID.randomUUID())
                .revisionNumber(1)
                .build());

        examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(practiceMatrix.getMatrixId())
                .subjectId(targetSubject.getSubjectId())
                .topicId(topic1.getTopicId())
                .difficultyLevel(DifficultyLevel.HARD)
                .cloId(null)
                .numQuestions(3)
                .weightPercent(BigDecimal.ONE)
                .build());

        Exam practiceExam = examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .matrixId(practiceMatrix.getMatrixId())
                .title("Đề thi luyện tập " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());

        // PRACTICE sinh đề thành công nhờ fallback
        int added = examService.autoGenerateQuestions(practiceExam.getExamId(), instructor.getUserId());
        assertEquals(3, added);
        assertEquals(3, examQuestionRepository.countByExamId(practiceExam.getExamId()));
    }

    @Test
    @DisplayName("Test 19: Trọng số điểm (scoreWeight) của từng câu hỏi khớp đúng với MatrixDetail")
    void test19_scoringWeightsPreservedFromMatrixDetails() {
        QuestionBank q1 = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(q1, clo1);
        QuestionBank q2 = createQuestion(targetSubject, topic2, DifficultyLevel.HARD, ApprovalStatus.APPROVED);
        linkQuestionClo(q2, clo2);

        BigDecimal weight1 = new BigDecimal("2.5");
        BigDecimal weight2 = new BigDecimal("7.5");

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).weightPercent(weight1).build(),
                ExamMatrixDetail.builder().topicId(topic2.getTopicId()).difficultyLevel(DifficultyLevel.HARD).cloId(clo2.getCloId()).numQuestions(1).weightPercent(weight2).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId());
        assertEquals(2, questions.size());
        assertEquals(0, weight1.compareTo(questions.get(0).getScoreWeight()));
        assertEquals(0, weight2.compareTo(questions.get(1).getScoreWeight()));
    }

    @Test
    @DisplayName("Test 20: Phê duyệt câu hỏi (Question Governance) không bị phá vỡ")
    void test20_questionApprovalGovernancePreserved() {
        QuestionBank qApproved = createQuestion(targetSubject, topic1, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        linkQuestionClo(qApproved, clo1);

        ExamMatrix matrix = createPublishedFinalMatrix(List.of(
                ExamMatrixDetail.builder().topicId(topic1.getTopicId()).difficultyLevel(DifficultyLevel.EASY).cloId(clo1.getCloId()).numQuestions(1).build()
        ));
        Exam exam = createFinalExam(targetClass, matrix);

        examService.autoGenerateQuestions(exam.getExamId(), instructor.getUserId());

        ExamQuestion eq = examQuestionRepository.findByExamIdOrderByOrderIndexAsc(exam.getExamId()).get(0);
        QuestionBank selected = questionBankRepository.findById(eq.getQuestionId()).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, selected.getApprovalStatus(), "Câu hỏi được sinh phải luôn giữ nguyên trạng thái APPROVED");
    }
}
