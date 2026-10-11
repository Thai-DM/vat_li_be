package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
@Transactional
class Phase5Step5SecurityAcceptanceTest {

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamMatrixDetailRepository examMatrixDetailRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @Autowired
    private IUserRepository userRepository;

    private User admin;
    private User instructorA;
    private User instructorB;
    private User studentA;
    private User studentB;
    private User studentDropped;

    private Subject subject;
    private UUID semesterId;
    private Topic topic1;
    private Clo clo1;
    private Class classA;
    private Class classB;
    private QuestionBank q1;
    private QuestionBank q2;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .username("sec_admin_" + UUID.randomUUID())
                .email("sec_admin_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.ADMIN)
                .build());

        instructorA = userRepository.save(User.builder()
                .username("sec_inst_a_" + UUID.randomUUID())
                .email("sec_inst_a_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.INSTRUCTOR)
                .build());

        instructorB = userRepository.save(User.builder()
                .username("sec_inst_b_" + UUID.randomUUID())
                .email("sec_inst_b_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.INSTRUCTOR)
                .build());

        studentA = userRepository.save(User.builder()
                .username("sec_std_a_" + UUID.randomUUID())
                .email("sec_std_a_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.STUDENT)
                .build());

        studentB = userRepository.save(User.builder()
                .username("sec_std_b_" + UUID.randomUUID())
                .email("sec_std_b_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.STUDENT)
                .build());

        studentDropped = userRepository.save(User.builder()
                .username("sec_std_drop_" + UUID.randomUUID())
                .email("sec_std_drop_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.STUDENT)
                .build());

        subject = subjectRepository.save(Subject.builder()
                .subjectCode("SEC101_" + UUID.randomUUID().toString().substring(0, 6))
                .subjectName("Security Physics 101")
                .build());

        semesterId = UUID.randomUUID();

        topic1 = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .topicName("Sec Topic 1")
                .orderIndex(1)
                .build());

        clo1 = cloRepository.save(Clo.builder()
                .subjectId(subject.getSubjectId())
                .cloCode("CLO1")
                .description("Understand Security")
                .orderIndex(1)
                .isActive(true)
                .build());

        classA = classRepository.save(Class.builder()
                .subjectId(subject.getSubjectId())
                .semesterId(semesterId)
                .classCode("SEC_CLA_" + UUID.randomUUID().toString().substring(0, 6))
                .instructorId(instructorA.getUserId())
                .status(ClassStatus.ACTIVE)
                .build());

        classB = classRepository.save(Class.builder()
                .subjectId(subject.getSubjectId())
                .semesterId(semesterId)
                .classCode("SEC_CLB_" + UUID.randomUUID().toString().substring(0, 6))
                .instructorId(instructorB.getUserId())
                .status(ClassStatus.ACTIVE)
                .build());

        // Enrollments with subjectId and semesterId satisfying foreign keys
        classEnrollmentRepository.save(ClassEnrollment.builder()
                .classId(classA.getClassId())
                .subjectId(subject.getSubjectId())
                .semesterId(semesterId)
                .studentId(studentA.getUserId())
                .status(EnrollmentStatus.ACTIVE)
                .build());

        classEnrollmentRepository.save(ClassEnrollment.builder()
                .classId(classB.getClassId())
                .subjectId(subject.getSubjectId())
                .semesterId(semesterId)
                .studentId(studentB.getUserId())
                .status(EnrollmentStatus.ACTIVE)
                .build());

        classEnrollmentRepository.save(ClassEnrollment.builder()
                .classId(classA.getClassId())
                .subjectId(subject.getSubjectId())
                .semesterId(semesterId)
                .studentId(studentDropped.getUserId())
                .status(EnrollmentStatus.DROPPED)
                .build());

        // Question Bank Setup
        q1 = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic1.getTopicId())
                .content("Sec Question 1?")
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(admin.getUserId())
                .createdAt(Instant.now())
                .build());

        QuestionOption opt1A = questionOptionRepository.save(QuestionOption.builder()
                .questionId(q1.getQuestionId())
                .optionText("Ans A Correct")
                .isCorrect(true)
                .orderIndex(1)
                .build());

        questionOptionRepository.save(QuestionOption.builder()
                .questionId(q1.getQuestionId())
                .optionText("Ans B Wrong")
                .isCorrect(false)
                .orderIndex(2)
                .build());

        questionCloRepository.save(QuestionClo.builder()
                .subjectId(subject.getSubjectId())
                .questionId(q1.getQuestionId())
                .cloId(clo1.getCloId())
                .build());

        q2 = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic1.getTopicId())
                .content("Sec Question 2?")
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(admin.getUserId())
                .createdAt(Instant.now())
                .build());

        questionOptionRepository.save(QuestionOption.builder()
                .questionId(q2.getQuestionId())
                .optionText("Ans 2A Correct")
                .isCorrect(true)
                .orderIndex(1)
                .build());

        questionCloRepository.save(QuestionClo.builder()
                .subjectId(subject.getSubjectId())
                .questionId(q2.getQuestionId())
                .cloId(clo1.getCloId())
                .build());
    }

    @Test
    @DisplayName("E2E Common FINAL Workflow: DRAFT -> PUBLISH -> Bulk Provision -> Archive -> Clone Revision")
    void testEndToEndCommonFinalExamWorkflow() {
        // 1. Admin creates DRAFT Matrix
        ExamMatrixDetailRequestDTO detailDto = new ExamMatrixDetailRequestDTO();
        detailDto.setTopicId(topic1.getTopicId());
        detailDto.setDifficultyLevel(DifficultyLevel.EASY);
        detailDto.setCloId(clo1.getCloId());
        detailDto.setNumQuestions(2);
        detailDto.setWeightPercent(BigDecimal.valueOf(50.0));

        CreateExamMatrixDTO createMatrixDTO = new CreateExamMatrixDTO();
        createMatrixDTO.setSubjectId(subject.getSubjectId());
        createMatrixDTO.setMatrixName("Common Final Exam Matrix 2026");
        createMatrixDTO.setExamType(ExamType.FINAL);
        createMatrixDTO.setDetails(List.of(detailDto));

        ExamMatrixDTO matrixDTO = examMatrixService.createFinalExamMatrix(createMatrixDTO, admin.getUserId());
        assertNotNull(matrixDTO.getMatrixId());
        assertEquals(MatrixStatus.DRAFT, matrixDTO.getMatrixStatus());
        assertFalse(matrixDTO.getIsLocked());

        // 2. Validate Question Availability
        var validation = examMatrixService.validateMatrixQuestionAvailability(matrixDTO.getMatrixId());
        assertTrue(validation.isValid());

        // 3. Publish Matrix
        ExamMatrixDTO publishedMatrix = examMatrixService.publishMatrix(matrixDTO.getMatrixId(), admin.getUserId());
        assertEquals(MatrixStatus.PUBLISHED, publishedMatrix.getMatrixStatus());
        assertTrue(publishedMatrix.getIsLocked());

        // 4. Updating PUBLISHED matrix must fail
        UpdateExamMatrixDTO updateDto = new UpdateExamMatrixDTO();
        updateDto.setMatrixName("Modified Matrix");
        assertThrows(CustomException.class, () ->
                examMatrixService.updateExamMatrix(publishedMatrix.getMatrixId(), updateDto, admin.getUserId()));

        // 5. Bulk Provision FINAL for Class A and Class B
        BulkProvisionFinalExamDTO bulkDto = new BulkProvisionFinalExamDTO();
        bulkDto.setMatrixId(publishedMatrix.getMatrixId());
        bulkDto.setClassIds(List.of(classA.getClassId(), classB.getClassId()));
        bulkDto.setDurationMinutes(60);
        bulkDto.setStartTime(Instant.now().minus(10, ChronoUnit.MINUTES));
        bulkDto.setEndTime(Instant.now().plus(50, ChronoUnit.MINUTES));
        bulkDto.setAutoGenerateQuestions(true);

        BulkProvisionFinalExamResultDTO bulkResult = examService.bulkProvisionFinalExams(bulkDto, admin.getUserId());
        assertEquals(2, bulkResult.getTotalClassesProvisioned());
        assertEquals(2, bulkResult.getCreatedExams().size());

        ExamDTO examA = bulkResult.getCreatedExams().stream()
                .filter(e -> e.getClassId().equals(classA.getClassId()))
                .findFirst().orElseThrow();
        ExamDTO examB = bulkResult.getCreatedExams().stream()
                .filter(e -> e.getClassId().equals(classB.getClassId()))
                .findFirst().orElseThrow();

        assertNotEquals(examA.getExamId(), examB.getExamId());
        assertEquals(publishedMatrix.getMatrixId(), examA.getMatrixId());
        assertEquals(publishedMatrix.getMatrixId(), examB.getMatrixId());
        assertEquals(2, examA.getTotalQuestions());
        assertEquals(2, examB.getTotalQuestions());

        // 6. Student A takes Exam A successfully
        ExamAttemptDTO attemptA = examService.startAttempt(examA.getExamId(), studentA.getUserId());
        assertNotNull(attemptA.getAttemptId());
        assertEquals(AttemptStatus.IN_PROGRESS, attemptA.getStatus());

        // 7. Student B cannot access Exam A
        assertThrows(CustomException.class, () ->
                examService.startAttempt(examA.getExamId(), studentB.getUserId()));

        // 8. DROPPED Student cannot access Exam A
        assertThrows(CustomException.class, () ->
                examService.startAttempt(examA.getExamId(), studentDropped.getUserId()));

        // 9. Instructor B cannot view or modify Exam A
        assertThrows(CustomException.class, () ->
                examService.getExamAttempts(examA.getExamId(), instructorB.getUserId(), "INSTRUCTOR"));

        // 10. Student submits early: answers must remain confidential while exam is still active
        ExamAttemptDTO submittedAttempt = examService.submitAttempt(attemptA.getAttemptId(), studentA.getUserId());
        assertEquals(AttemptStatus.GRADED, submittedAttempt.getStatus());

        ExamAttemptDTO attemptDetail = examService.getAttemptDetail(attemptA.getAttemptId(), studentA.getUserId(), "STUDENT");
        // Verify answer confidentiality: correct option indicators must be hidden for ongoing FINAL exam
        assertNotNull(attemptDetail.getQuestions());
        for (StudentExamQuestionDTO q : attemptDetail.getQuestions()) {
            for (StudentQuestionOptionDTO opt : q.getOptions()) {
                assertNull(opt.getIsCorrect(), "Answer options should be hidden before exam end time has passed");
            }
        }

        // 11. Archive Matrix
        ExamMatrixDTO archivedMatrix = examMatrixService.archiveMatrix(publishedMatrix.getMatrixId(), admin.getUserId());
        assertEquals(MatrixStatus.ARCHIVED, archivedMatrix.getMatrixStatus());

        // 12. Provisioning from ARCHIVED matrix must fail
        assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(bulkDto, admin.getUserId()));

        // 13. Clone Revision creates revision 2 in DRAFT without affecting revision 1
        ExamMatrixDTO rev2 = examMatrixService.cloneMatrixRevision(archivedMatrix.getMatrixId(), admin.getUserId());
        assertEquals(2, rev2.getRevisionNumber());
        assertEquals(MatrixStatus.DRAFT, rev2.getMatrixStatus());
        assertEquals(archivedMatrix.getMatrixFamilyId(), rev2.getMatrixFamilyId());
        assertNotEquals(archivedMatrix.getMatrixId(), rev2.getMatrixId());
    }

    @Test
    @DisplayName("Audit Bypass of FINAL permissions: INSTRUCTOR cannot mutate FINAL exam or matrix")
    void testInstructorCannotBypassFinalPermissions() {
        // Setup published matrix and an exam
        ExamMatrixDetailRequestDTO detailDto = new ExamMatrixDetailRequestDTO();
        detailDto.setTopicId(topic1.getTopicId());
        detailDto.setDifficultyLevel(DifficultyLevel.EASY);
        detailDto.setNumQuestions(1);

        CreateExamMatrixDTO createMatrixDTO = new CreateExamMatrixDTO();
        createMatrixDTO.setSubjectId(subject.getSubjectId());
        createMatrixDTO.setMatrixName("Common Final Matrix RBAC");
        createMatrixDTO.setExamType(ExamType.FINAL);
        createMatrixDTO.setDetails(List.of(detailDto));

        ExamMatrixDTO matrixDTO = examMatrixService.createFinalExamMatrix(createMatrixDTO, admin.getUserId());
        examMatrixService.publishMatrix(matrixDTO.getMatrixId(), admin.getUserId());

        BulkProvisionFinalExamDTO bulkDto = new BulkProvisionFinalExamDTO();
        bulkDto.setMatrixId(matrixDTO.getMatrixId());
        bulkDto.setClassIds(List.of(classA.getClassId()));
        bulkDto.setDurationMinutes(60);
        bulkDto.setStartTime(Instant.now().minus(5, ChronoUnit.MINUTES));
        bulkDto.setEndTime(Instant.now().plus(55, ChronoUnit.MINUTES));
        bulkDto.setAutoGenerateQuestions(true);

        BulkProvisionFinalExamResultDTO bulkResult = examService.bulkProvisionFinalExams(bulkDto, admin.getUserId());
        UUID finalExamId = bulkResult.getCreatedExams().get(0).getExamId();

        // 1. Instructor cannot create FINAL exam directly
        CreateExamDTO createFinalDto = new CreateExamDTO();
        createFinalDto.setClassId(classA.getClassId());
        createFinalDto.setTitle("Illegal Instructor FINAL");
        createFinalDto.setExamType(ExamType.FINAL);
        createFinalDto.setDurationMinutes(60);
        assertThrows(CustomException.class, () ->
                examService.createExam(createFinalDto, instructorA.getUserId()));

        // 2. Instructor cannot update FINAL exam
        UpdateExamDTO updateExamDto = new UpdateExamDTO();
        updateExamDto.setTitle("Tampered Title");
        assertThrows(CustomException.class, () ->
                examService.updateExam(finalExamId, updateExamDto, instructorA.getUserId()));

        // 3. Instructor cannot delete FINAL exam
        assertThrows(CustomException.class, () ->
                examService.deleteExam(finalExamId, instructorA.getUserId()));

        // 4. Instructor cannot add questions to FINAL exam
        AddExamQuestionDTO addQDto = new AddExamQuestionDTO();
        addQDto.setQuestionId(q2.getQuestionId());
        assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(finalExamId, addQDto, instructorA.getUserId()));

        // 5. Instructor cannot remove questions from FINAL exam
        assertThrows(CustomException.class, () ->
                examService.removeQuestionFromExam(finalExamId, q1.getQuestionId(), instructorA.getUserId()));

        // 6. Instructor cannot auto-generate questions for FINAL exam
        assertThrows(CustomException.class, () ->
                examService.autoGenerateQuestions(finalExamId, instructorA.getUserId()));

        // 7. Instructor cannot modify Common FINAL Matrix
        UpdateExamMatrixDTO updateMatrixDto = new UpdateExamMatrixDTO();
        updateMatrixDto.setMatrixName("Hacked Matrix");
        assertThrows(CustomException.class, () ->
                examMatrixService.updateExamMatrix(matrixDTO.getMatrixId(), updateMatrixDto, instructorA.getUserId()));
    }

    @Test
    @DisplayName("Empty Exam Protection: Student cannot start exam with 0 questions")
    void testStudentCannotStartEmptyExam() {
        Exam emptyExam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Empty Exam")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(50, ChronoUnit.MINUTES))
                .createdBy(admin.getUserId())
                .createdAt(Instant.now())
                .build());

        // Attempting to start attempt on empty exam must throw BAD_REQUEST
        CustomException ex = assertThrows(CustomException.class, () ->
                examService.startAttempt(emptyExam.getExamId(), studentA.getUserId()));
        assertTrue(ex.getMessage().contains("chưa có câu hỏi"));
    }

    @Test
    @DisplayName("Historical Attempt Protection: Cannot modify or delete exam with existing attempts")
    void testCannotModifyOrDeleteExamWithExistingAttempts() {
        // Create PRACTICE exam and an attempt
        Exam exam = examRepository.save(Exam.builder()
                .classId(classA.getClassId())
                .title("Practice Exam")
                .examType(ExamType.PRACTICE)
                .durationMinutes(60)
                .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endTime(Instant.now().plus(50, ChronoUnit.MINUTES))
                .createdBy(instructorA.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(q1.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());

        // Student starts attempt
        examService.startAttempt(exam.getExamId(), studentA.getUserId());

        // Instructor attempts to update exam -> must be blocked
        UpdateExamDTO updateDto = new UpdateExamDTO();
        updateDto.setDurationMinutes(90);
        assertThrows(CustomException.class, () ->
                examService.updateExam(exam.getExamId(), updateDto, instructorA.getUserId()));

        // Instructor attempts to delete exam -> must be blocked
        assertThrows(CustomException.class, () ->
                examService.deleteExam(exam.getExamId(), instructorA.getUserId()));
    }
}
