package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.request.UpdateExamDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class QuestionAndExamSafetyTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IQuestionBankService questionBankService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private User instructor;
    private User otherInstructor;
    private User student;
    private Class targetClass;
    private Topic targetTopic;

    private Subject otherSubject;
    private Topic otherTopic;

    @BeforeEach
    void setUp() {
        instructor = userRepository.findByUsername("gv_nguyen");
        assertNotNull(instructor, "Instructor gv_nguyen must exist");

        otherInstructor = userRepository.findByUsername("gv_tran");
        assertNotNull(otherInstructor, "Instructor gv_tran must exist");

        student = userRepository.findByUsername("sv_an");
        assertNotNull(student, "Student sv_an must exist");

        targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), instructor.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Class for gv_nguyen not found"));

        targetTopic = topicRepository.save(Topic.builder()
                .subjectId(targetClass.getSubjectId())
                .topicName("Target Topic " + UUID.randomUUID())
                .orderIndex(1)
                .build());

        otherSubject = subjectRepository.save(Subject.builder()
                .subjectCode("OTHER_" + UUID.randomUUID().toString().substring(0, 8))
                .subjectName("Other Subject Test " + UUID.randomUUID())
                .description("Test Subject Description")
                .isActive(true)
                .build());

        otherTopic = topicRepository.save(Topic.builder()
                .subjectId(otherSubject.getSubjectId())
                .topicName("Other Topic " + UUID.randomUUID())
                .orderIndex(1)
                .build());
    }

    private Exam createTestExam(Instant startTime, Instant endTime) {
        return examRepository.save(Exam.builder()
                .classId(targetClass.getClassId())
                .title("Test Safety Exam " + UUID.randomUUID())
                .examType(ExamType.PRACTICE)
                .durationMinutes(45)
                .startTime(startTime != null ? startTime : Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(endTime != null ? endTime : Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());
    }

    private QuestionBank createQuestionWithStatus(Topic topic, ApprovalStatus status, String content) {
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(topic.getSubjectId())
                .topicId(topic.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .questionType(QuestionType.MCQ_SINGLE)
                .approvalStatus(status)
                .content(content)
                .createdBy(instructor.getUserId())
                .createdAt(Instant.now())
                .build());

        questionOptionRepository.save(QuestionOption.builder()
                .questionId(q.getQuestionId())
                .optionText("Option A Correct")
                .isCorrect(true)
                .orderIndex(0)
                .build());

        questionOptionRepository.save(QuestionOption.builder()
                .questionId(q.getQuestionId())
                .optionText("Option B Wrong")
                .isCorrect(false)
                .orderIndex(1)
                .build());

        return q;
    }

    @Test
    @DisplayName("Test 1: Thêm câu hỏi APPROVED đúng Subject thành công")
    void test1_AddQuestionToExam_ApprovedSameSubject_Success() {
        Exam exam = createTestExam(null, null);
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu APPROVED cùng môn");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .scoreWeight(BigDecimal.valueOf(1.5))
                .orderIndex(1)
                .build();

        assertDoesNotThrow(() -> examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId()));

        assertTrue(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), q.getQuestionId()));
        assertEquals(1, examQuestionRepository.countByExamId(exam.getExamId()));
    }

    @Test
    @DisplayName("Test 2: Thêm câu hỏi DRAFT vào đề bị từ chối 400 Bad Request")
    void test2_AddQuestionToExam_Draft_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank qDraft = createQuestionWithStatus(targetTopic, ApprovalStatus.DRAFT, "Câu DRAFT chưa duyệt");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(qDraft.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Only APPROVED questions can be added to an exam"));
        assertFalse(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), qDraft.getQuestionId()));
    }

    @Test
    @DisplayName("Test 3: Thêm câu hỏi PENDING vào đề bị từ chối 400 Bad Request")
    void test3_AddQuestionToExam_Pending_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank qPending = createQuestionWithStatus(targetTopic, ApprovalStatus.PENDING, "Câu PENDING đang chờ duyệt");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(qPending.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Only APPROVED questions can be added to an exam"));
        assertFalse(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), qPending.getQuestionId()));
    }

    @Test
    @DisplayName("Test 4: Thêm câu hỏi REJECTED vào đề bị từ chối 400 Bad Request")
    void test4_AddQuestionToExam_Rejected_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank qRejected = createQuestionWithStatus(targetTopic, ApprovalStatus.REJECTED, "Câu REJECTED đã bị từ chối");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(qRejected.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Only APPROVED questions can be added to an exam"));
        assertFalse(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), qRejected.getQuestionId()));
    }

    @Test
    @DisplayName("Test 5: Thêm câu hỏi khác Subject bị từ chối 400 Bad Request")
    void test5_AddQuestionToExam_DifferentSubject_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank qOtherSubject = createQuestionWithStatus(otherTopic, ApprovalStatus.APPROVED, "Câu APPROVED môn khác");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(qOtherSubject.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Question does not belong to the same subject as the exam"));
        assertFalse(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), qOtherSubject.getQuestionId()));
    }

    @Test
    @DisplayName("Test 6: Giảng viên không có quyền quản lý đề bị từ chối 403 Forbidden")
    void test6_AddQuestionToExam_UnauthorizedInstructor_Forbidden() {
        Exam exam = createTestExam(null, null);
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu APPROVED hợp lệ");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();

        // otherInstructor (gv_tran) is not teaching targetClass
        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, otherInstructor.getUserId())
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Giảng viên không được phân công") || ex.getMessage().contains("Access denied"));
        assertFalse(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), q.getQuestionId()));
    }

    @Test
    @DisplayName("Test 7: Câu hỏi đã được sử dụng trong đề CÓ THỂ được cập nhật sửa lỗi/typo và bảo toàn option ID")
    void test7_UpdateQuestion_AlreadyUsedInExam_AllowedAndPreservesOptionIds() {
        Exam exam = createTestExam(null, null);
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu hỏi đã đưa vào đề");

        // Add question to exam
        AddExamQuestionDTO addDto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();
        examService.addQuestionToExam(exam.getExamId(), addDto, instructor.getUserId());

        List<QuestionOption> originalOptions = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId());
        assertEquals(2, originalOptions.size());

        // Update question that is now referenced in exam_questions (fixing a typo)
        CreateQuestionDTO updateDto = CreateQuestionDTO.builder()
                .subjectId(targetTopic.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Nội dung câu hỏi đã được sửa đúng!")
                .difficultyLevel(DifficultyLevel.HARD)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("New Option 1 corrected")
                                .isCorrect(true)
                                .orderIndex(0)
                                .build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("New Option 2 corrected")
                                .isCorrect(false)
                                .orderIndex(1)
                                .build()
                ))
                .build();

        QuestionBankDTO updatedDto = questionBankService.updateQuestion(q.getQuestionId(), updateDto, instructor.getUserId(), "INSTRUCTOR");
        assertNotNull(updatedDto);
        assertEquals("Nội dung câu hỏi đã được sửa đúng!", updatedDto.getContent());

        // Verify options in DB were updated in-place preserving option IDs
        QuestionBank preservedQ = questionBankRepository.findById(q.getQuestionId()).orElseThrow();
        assertEquals("Nội dung câu hỏi đã được sửa đúng!", preservedQ.getContent());
        List<QuestionOption> preservedOptions = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId());
        assertEquals(2, preservedOptions.size());
        assertEquals(originalOptions.get(0).getOptionId(), preservedOptions.get(0).getOptionId());
    }

    @Test
    @DisplayName("Test 8: Câu hỏi đã được sử dụng trong đề không thể bị xóa vật lý")
    void test8_DeleteQuestion_AlreadyUsedInExam_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu hỏi đã đưa vào đề để xóa");

        // Add question to exam
        AddExamQuestionDTO addDto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();
        examService.addQuestionToExam(exam.getExamId(), addDto, instructor.getUserId());

        // Attempt to delete question
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.deleteQuestion(q.getQuestionId(), instructor.getUserId(), "INSTRUCTOR")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Cannot delete question that has already been used in an exam"));

        // Question and options still exist in database
        assertTrue(questionBankRepository.existsById(q.getQuestionId()));
        assertFalse(questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId()).isEmpty());
    }

    @Test
    @DisplayName("Test 9: Câu hỏi nháp chưa sử dụng vẫn có thể được sửa theo quyền hiện tại")
    void test9_UpdateQuestion_UnusedDraft_CanBeEdited() {
        QuestionBank draftQ = createQuestionWithStatus(targetTopic, ApprovalStatus.DRAFT, "Bản nháp ban đầu");

        CreateQuestionDTO updateDto = CreateQuestionDTO.builder()
                .subjectId(targetTopic.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Bản nháp đã được chỉnh sửa hoàn thiện")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("Phương án mới duy nhất đúng")
                                .isCorrect(true)
                                .orderIndex(0)
                                .build(),
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("Phương án sai mới")
                                .isCorrect(false)
                                .orderIndex(1)
                                .build()
                ))
                .build();

        QuestionBankDTO updated = questionBankService.updateQuestion(
                draftQ.getQuestionId(), updateDto, instructor.getUserId(), "INSTRUCTOR"
        );

        assertNotNull(updated);
        assertEquals("Bản nháp đã được chỉnh sửa hoàn thiện", updated.getContent());
        assertEquals(DifficultyLevel.MEDIUM, updated.getDifficultyLevel());
        assertEquals(ApprovalStatus.DRAFT, updated.getApprovalStatus(), "Câu hỏi draft khi cập nhật vẫn giữ trạng thái DRAFT");
        assertEquals(2, updated.getOptions().size());
    }

    @Test
    @DisplayName("Test 10: Đề thi đã có sinh viên làm bài (attempts) không được phép sửa cấu trúc câu hỏi")
    void test10_AddQuestionToExam_ExamWithAttempts_Rejected() {
        Exam exam = createTestExam(null, null);

        // Create a student attempt on this exam
        examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .attemptNumber(1)
                .build());

        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu muốn thêm vào đề đang thi");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Cannot modify questions for an exam that already has student attempts"));
    }

    @Test
    @DisplayName("Test 11: Không được thêm trùng câu hỏi vào cùng một đề thi")
    void test11_AddQuestionToExam_DuplicateQuestion_Rejected() {
        Exam exam = createTestExam(null, null);
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu thêm 2 lần");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();

        examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId());

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(exam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Question already added to this exam"));
    }

    @Test
    @DisplayName("Test 12: Đề thi đã kết thúc (endTime trong quá khứ) không cho phép thêm câu hỏi")
    void test12_AddQuestionToExam_ExamEnded_Rejected() {
        Instant pastStart = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant pastEnd = Instant.now().minus(1, ChronoUnit.HOURS);
        Exam closedExam = createTestExam(pastStart, pastEnd);

        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.APPROVED, "Câu thêm vào đề đã kết thúc");

        AddExamQuestionDTO dto = AddExamQuestionDTO.builder()
                .questionId(q.getQuestionId())
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.addQuestionToExam(closedExam.getExamId(), dto, instructor.getUserId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Cannot modify questions for an exam that has already ended"));
    }

    @Test
    @DisplayName("Test 13: Xóa câu hỏi nháp chưa sử dụng thành công bởi người tạo")
    void test13_DeleteQuestion_UnusedDraft_Success() {
        QuestionBank draftQ = createQuestionWithStatus(targetTopic, ApprovalStatus.DRAFT, "Bản nháp chuẩn bị xóa");
        UUID qId = draftQ.getQuestionId();

        assertDoesNotThrow(() ->
                questionBankService.deleteQuestion(qId, instructor.getUserId(), "INSTRUCTOR")
        );

        assertFalse(questionBankRepository.existsById(qId));
        assertTrue(questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(qId).isEmpty());
    }

    @Test
    @DisplayName("Test 14: Người khác (không phải creator, không phải ADMIN) sửa câu hỏi bị 403 Forbidden")
    void test14_UpdateQuestion_UnauthorizedUser_Forbidden() {
        QuestionBank q = createQuestionWithStatus(targetTopic, ApprovalStatus.DRAFT, "Bản nháp của gv_nguyen");

        CreateQuestionDTO updateDto = CreateQuestionDTO.builder()
                .subjectId(targetTopic.getSubjectId())
                .topicId(targetTopic.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("gv_tran cố gắng sửa câu của gv_nguyen")
                .difficultyLevel(DifficultyLevel.EASY)
                .options(List.of(
                        CreateQuestionDTO.CreateQuestionOptionDTO.builder()
                                .content("A")
                                .isCorrect(true)
                                .orderIndex(0)
                                .build()
                ))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.updateQuestion(q.getQuestionId(), updateDto, otherInstructor.getUserId(), "INSTRUCTOR")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("You do not have permission to update this question"));
    }

    @Test
    @DisplayName("Test 15: Đề thi đã có thí sinh làm bài CÓ THỂ được gia hạn thời gian (durationMinutes, startTime, endTime, title)")
    void test15_UpdateExam_WithExistingAttempts_AllowsTimeExtensionAndTitleUpdate() {
        Exam baseExam = createTestExam(Instant.now().minus(30, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES));
        baseExam.setDurationMinutes(45);
        Exam exam = examRepository.save(baseExam);

        // Tạo 1 attempt giả định đã có thí sinh làm bài
        ExamAttempt attempt = ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
        examAttemptRepository.save(attempt);

        // Giáo viên gia hạn thêm thời gian thi do trục trặc
        Instant newEndTime = Instant.now().plus(90, ChronoUnit.MINUTES);
        UpdateExamDTO updateDto = UpdateExamDTO.builder()
                .title("Kỳ thi đã được gia hạn thêm giờ")
                .durationMinutes(75)
                .endTime(newEndTime)
                .build();

        ExamDTO result = examService.updateExam(exam.getExamId(), updateDto, instructor.getUserId());
        assertNotNull(result);
        assertEquals(75, result.getDurationMinutes());
        assertEquals("Kỳ thi đã được gia hạn thêm giờ", result.getTitle());

        Exam reloaded = examRepository.findById(exam.getExamId()).orElseThrow();
        assertEquals(75, reloaded.getDurationMinutes());
        assertEquals("Kỳ thi đã được gia hạn thêm giờ", reloaded.getTitle());
    }

    @Test
    @DisplayName("Test 16: Đề thi đã có thí sinh làm bài KHÔNG THỂ thay đổi examType hoặc matrixId")
    void test16_UpdateExam_WithExistingAttempts_DisallowsChangingMatrixOrExamType() {
        Exam exam = examRepository.save(createTestExam(Instant.now().minus(30, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES)));

        ExamAttempt attempt = ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(student.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
        examAttemptRepository.save(attempt);

        UpdateExamDTO changeTypeDto = UpdateExamDTO.builder()
                .examType(ExamType.FINAL)
                .build();

        CustomException exType = assertThrows(CustomException.class, () ->
                examService.updateExam(exam.getExamId(), changeTypeDto, instructor.getUserId())
        );
        assertEquals(HttpStatus.BAD_REQUEST, exType.getHttpStatus());
        assertTrue(exType.getMessage().contains("Không thể thay đổi hình thức thi (examType) của kỳ thi đã có thí sinh làm bài"));

        UpdateExamDTO changeMatrixDto = UpdateExamDTO.builder()
                .matrixId(UUID.randomUUID())
                .build();

        CustomException exMatrix = assertThrows(CustomException.class, () ->
                examService.updateExam(exam.getExamId(), changeMatrixDto, instructor.getUserId())
        );
        assertEquals(HttpStatus.BAD_REQUEST, exMatrix.getHttpStatus());
        assertTrue(exMatrix.getMessage().contains("Không thể thay đổi ma trận đề thi (matrixId) của kỳ thi đã có thí sinh làm bài"));
    }
}
