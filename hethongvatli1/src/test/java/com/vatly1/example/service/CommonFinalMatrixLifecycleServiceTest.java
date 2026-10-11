package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
import com.vatly1.example.model.response.MatrixValidationResultDTO;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class CommonFinalMatrixLifecycleServiceTest {

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamMatrixDetailRepository examMatrixDetailRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @Autowired
    private IUserRepository userRepository;

    private User adminUser;
    private User instructorUser;
    private Subject testSubject;
    private Topic topic1;
    private Topic topic2;
    private Clo clo1;
    private Clo clo2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(User.builder()
                .username("admin_" + suffix)
                .email("admin_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .build());

        instructorUser = userRepository.save(User.builder()
                .username("inst_" + suffix)
                .email("inst_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.INSTRUCTOR)
                .build());

        testSubject = subjectRepository.save(Subject.builder()
                .subjectCode("PH_" + suffix)
                .subjectName("Vật lý đại cương " + suffix)
                .isActive(true)
                .build());

        topic1 = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .topicName("Cơ học chất điểm " + suffix)
                .orderIndex(1)
                .build());

        topic2 = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .topicName("Nhiệt học " + suffix)
                .orderIndex(2)
                .build());

        clo1 = cloRepository.save(Clo.builder()
                .subjectId(testSubject.getSubjectId())
                .cloCode("CLO1_" + suffix)
                .description("Vận dụng định luật Newton")
                .orderIndex(1)
                .isActive(true)
                .build());

        clo2 = cloRepository.save(Clo.builder()
                .subjectId(testSubject.getSubjectId())
                .cloCode("CLO2_" + suffix)
                .description("Tính công và nhiệt")
                .orderIndex(2)
                .isActive(true)
                .build());
    }

    private void seedApprovedQuestion(UUID topicId, DifficultyLevel difficulty, Clo... clos) {
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(testSubject.getSubjectId())
                .topicId(topicId)
                .content("Câu hỏi kiểm thử " + UUID.randomUUID())
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(difficulty)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(adminUser.getUserId())
                .build());

        if (clos != null) {
            for (Clo c : clos) {
                if (c != null) {
                    questionCloRepository.save(QuestionClo.builder()
                            .questionId(q.getQuestionId())
                            .cloId(c.getCloId())
                            .subjectId(testSubject.getSubjectId())
                            .build());
                }
            }
        }
    }

    @Test
    @DisplayName("1. ADMIN tạo Common FINAL Matrix ở trạng thái DRAFT thành công")
    void testAdminCreateFinalMatrixDraft_Success() {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Ma trận Thi Cuối Kỳ Chuẩn")
                .examType(ExamType.FINAL)
                .totalPoints(BigDecimal.valueOf(10.0))
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(2)
                                .weightPercent(BigDecimal.ONE)
                                .build()
                ))
                .build();

        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(dto, adminUser.getUserId());

        assertNotNull(created);
        assertNotNull(created.getMatrixId());
        assertNotNull(created.getMatrixFamilyId());
        assertEquals(MatrixStatus.DRAFT, created.getMatrixStatus());
        assertFalse(created.getIsLocked());
        assertEquals(1, created.getRevisionNumber());
        assertEquals(ExamType.FINAL, created.getExamType());
        assertEquals(1, created.getDetails().size());
        assertEquals(2, created.getTotalQuestions());
    }

    @Test
    @DisplayName("2. INSTRUCTOR không được tạo Common FINAL Matrix (403 FORBIDDEN)")
    void testInstructorCreateFinalMatrix_Forbidden() {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Giảng viên tạo FINAL trái phép")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.createFinalExamMatrix(dto, instructorUser.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());

        CustomException ex2 = assertThrows(CustomException.class, () ->
                examMatrixService.createExamMatrix(dto, instructorUser.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex2.getHttpStatus());
    }

    @Test
    @DisplayName("3. INSTRUCTOR vẫn tạo được PRACTICE Matrix cho lớp mình phụ trách")
    void testInstructorCreatePracticeMatrix_Success() {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Ma trận luyện tập")
                .examType(ExamType.PRACTICE)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(3)
                                .build()
                ))
                .build();

        ExamMatrixDTO created = examMatrixService.createExamMatrix(dto, instructorUser.getUserId());
        assertNotNull(created);
        assertEquals(ExamType.PRACTICE, created.getExamType());
    }

    @Test
    @DisplayName("4. ADMIN cập nhật DRAFT Matrix thành công")
    void testAdminUpdateDraftMatrix_Success() {
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("DRAFT Matrix v1")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(2)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        UpdateExamMatrixDTO updateDto = UpdateExamMatrixDTO.builder()
                .matrixName("DRAFT Matrix v1 - Updated")
                .totalPoints(BigDecimal.valueOf(100.0))
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.MEDIUM)
                                .numQuestions(4)
                                .build()
                ))
                .build();

        ExamMatrixDTO updated = examMatrixService.updateExamMatrix(created.getMatrixId(), updateDto, adminUser.getUserId());
        assertEquals("DRAFT Matrix v1 - Updated", updated.getMatrixName());
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(updated.getTotalPoints()));
        assertEquals(4, updated.getTotalQuestions());
        assertEquals(DifficultyLevel.MEDIUM, updated.getDetails().get(0).getDifficultyLevel());
    }

    @Test
    @DisplayName("5. Không được cập nhật Matrix đã PUBLISHED (400 BAD REQUEST)")
    void testCannotUpdatePublishedMatrix() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Publish")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(2)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        // Publish
        examMatrixService.publishMatrix(created.getMatrixId(), adminUser.getUserId());

        // Attempt update
        UpdateExamMatrixDTO updateDto = UpdateExamMatrixDTO.builder()
                .matrixName("Try Update Published")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.updateExamMatrix(created.getMatrixId(), updateDto, adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("đã khóa"));
    }

    @Test
    @DisplayName("6. INSTRUCTOR không thể chỉnh sửa FINAL Matrix (403 FORBIDDEN)")
    void testInstructorCannotUpdateFinalMatrix() {
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("FINAL Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        UpdateExamMatrixDTO updateDto = UpdateExamMatrixDTO.builder()
                .matrixName("Instructor hacked name")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.updateExamMatrix(created.getMatrixId(), updateDto, instructorUser.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("7. Validate đủ câu hỏi APPROVED theo ma trận thành công")
    void testValidateMatrix_SufficientQuestions() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix Validation Test")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(2)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        MatrixValidationResultDTO result = examMatrixService.validateMatrixQuestionAvailability(created.getMatrixId());
        assertTrue(result.isValid());
        assertEquals(2, result.getTotalRequired());
        assertEquals(2, result.getTotalAvailable());
    }

    @Test
    @DisplayName("8. Validate thiếu câu hỏi APPROVED theo ma trận thất bại")
    void testValidateMatrix_InsufficientQuestions() {
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix Validation Fail Test")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.HARD)
                                .numQuestions(5)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        MatrixValidationResultDTO result = examMatrixService.validateMatrixQuestionAvailability(created.getMatrixId());
        assertFalse(result.isValid());
        assertEquals(5, result.getTotalRequired());
        assertEquals(0, result.getTotalAvailable());
    }

    @Test
    @DisplayName("9. Validate nhiều CLO giao nhau dùng kết quả Max-Flow")
    void testValidateMatrix_IntersectingClos_DinicMaxFlow() {
        // Q1 covers clo1 & clo2
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.MEDIUM, clo1, clo2);
        // Q2 covers clo2 only
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.MEDIUM, clo2);

        // Matrix needs 1 for clo1 and 1 for clo2
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix Intersecting CLOs")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.MEDIUM)
                                .numQuestions(1)
                                .build(),
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo2.getCloId())
                                .difficultyLevel(DifficultyLevel.MEDIUM)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        MatrixValidationResultDTO result = examMatrixService.validateMatrixQuestionAvailability(created.getMatrixId());
        assertTrue(result.isValid());
        assertEquals(2, result.getTotalRequired());
        assertEquals(2, result.getTotalAvailable());
    }

    @Test
    @DisplayName("10. Publish ma trận hợp lệ thành công -> status=PUBLISHED, isLocked=true")
    void testPublishMatrix_Success() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Publish Successfully")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        ExamMatrixDTO published = examMatrixService.publishMatrix(created.getMatrixId(), adminUser.getUserId());
        assertEquals(MatrixStatus.PUBLISHED, published.getMatrixStatus());
        assertTrue(published.getIsLocked());

        // Verify in DB
        ExamMatrix dbMatrix = examMatrixRepository.findById(created.getMatrixId()).orElseThrow();
        assertEquals(MatrixStatus.PUBLISHED, dbMatrix.getStatus());
        assertTrue(dbMatrix.getIsLocked());
    }

    @Test
    @DisplayName("11. Publish ma trận thiếu câu hỏi thất bại (400 BAD REQUEST)")
    void testPublishMatrix_InsufficientQuestions_BadRequest() {
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix Lacking Questions")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.HARD)
                                .numQuestions(10)
                                .build()
                ))
                .build();

        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.publishMatrix(created.getMatrixId(), adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Ngân hàng câu hỏi không đủ"));
    }

    @Test
    @DisplayName("12. Archive ma trận thành công từ PUBLISHED -> status=ARCHIVED, isLocked=true")
    void testArchiveMatrix_Success() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Archive")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        examMatrixService.publishMatrix(created.getMatrixId(), adminUser.getUserId());

        ExamMatrixDTO archived = examMatrixService.archiveMatrix(created.getMatrixId(), adminUser.getUserId());
        assertEquals(MatrixStatus.ARCHIVED, archived.getMatrixStatus());
        assertTrue(archived.getIsLocked());

        ExamMatrix dbMatrix = examMatrixRepository.findById(created.getMatrixId()).orElseThrow();
        assertEquals(MatrixStatus.ARCHIVED, dbMatrix.getStatus());
        assertTrue(dbMatrix.getIsLocked());
    }

    @Test
    @DisplayName("13. Không thể lưu trữ trực tiếp ma trận DRAFT (400 BAD REQUEST)")
    void testArchiveDraftMatrix_BadRequest() {
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("DRAFT Matrix to Archive")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.archiveMatrix(created.getMatrixId(), adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("PUBLISHED"));
    }

    @Test
    @DisplayName("14. Clone Matrix Revision tạo bản ghi mới (rev 2, DRAFT, isLocked=false), không sửa bản ghi cũ")
    void testCloneMatrixRevision_Success() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Common Matrix Family A")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .weightPercent(BigDecimal.valueOf(2.5))
                                .build()
                ))
                .build();
        ExamMatrixDTO source = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        examMatrixService.publishMatrix(source.getMatrixId(), adminUser.getUserId());

        // Clone revision
        ExamMatrixDTO cloned = examMatrixService.cloneMatrixRevision(source.getMatrixId(), adminUser.getUserId());

        assertNotNull(cloned);
        assertNotEquals(source.getMatrixId(), cloned.getMatrixId());
        assertEquals(source.getMatrixFamilyId(), cloned.getMatrixFamilyId());
        assertEquals(2, cloned.getRevisionNumber());
        assertEquals(MatrixStatus.DRAFT, cloned.getMatrixStatus());
        assertFalse(cloned.getIsLocked());
        assertEquals(ExamType.FINAL, cloned.getExamType());
        assertEquals(source.getSubjectId(), cloned.getSubjectId());
        assertEquals(1, cloned.getDetails().size());

        // Verify detail IDs are unique and not shared
        UUID sourceDetailId = source.getDetails().get(0).getDetailId();
        UUID clonedDetailId = cloned.getDetails().get(0).getDetailId();
        assertNotEquals(sourceDetailId, clonedDetailId);
        assertEquals(0, BigDecimal.valueOf(2.5).compareTo(cloned.getDetails().get(0).getWeightPercent()));

        // Verify source matrix was NOT touched
        ExamMatrix sourceDb = examMatrixRepository.findById(source.getMatrixId()).orElseThrow();
        assertEquals(MatrixStatus.PUBLISHED, sourceDb.getStatus());
        assertTrue(sourceDb.getIsLocked());
        assertEquals(1, sourceDb.getRevisionNumber());
    }

    @Test
    @DisplayName("15. Xóa ma trận PUBLISHED bị từ chối (400 BAD REQUEST)")
    void testDeletePublishedMatrix_BadRequest() {
        seedApprovedQuestion(topic1.getTopicId(), DifficultyLevel.EASY, clo1);

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix Cannot Delete")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic1.getTopicId())
                                .cloId(clo1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        examMatrixService.publishMatrix(created.getMatrixId(), adminUser.getUserId());

        CustomException ex = assertThrows(CustomException.class, () ->
                examMatrixService.deleteExamMatrix(created.getMatrixId(), adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("đã khóa"));
    }
}
