package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamAnswer;
import com.vatly1.example.entity.ExamAttempt;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.ExamQuestion;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamMatrixService;
import com.vatly1.example.service.IExamService;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("pgtest")
@org.springframework.test.context.ContextConfiguration(initializers = PostgreSqlTestContextInitializer.class)
@EnabledIfEnvironmentVariable(named = "RUN_PG_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgreSqlCommonFinalMatrixIntegrityIntegrationTest {

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamMatrixDetailRepository examMatrixDetailRepository;

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamService examService;

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
    private ICloRepository cloRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Subject subjectA;
    private Subject subjectB;
    private Semester semester;
    private Topic topicA;
    private Topic topicB;
    private Clo cloA;
    private Clo cloB;
    private Class classA1;
    private Class classA2;
    private User testUser;
    private User adminUser;

    private final List<UUID> createdAnswerIds = new ArrayList<>();
    private final List<UUID> createdAttemptIds = new ArrayList<>();
    private final List<UUID> createdExamIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdDetailIds = new ArrayList<>();
    private final List<UUID> createdMatrixIds = new ArrayList<>();
    private final List<UUID> createdCloIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdTopicIds = new ArrayList<>();
    private final List<UUID> createdSemesterIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    @BeforeAll
    void verifySchema() {
        // 1. Verify exam_matrix columns
        for (String col : List.of("matrix_family_id", "revision_number", "row_version", "status", "is_locked")) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'exam_matrix' AND column_name = ?",
                    Integer.class, col
            );
            assertEquals(1, count, "Column exam_matrix." + col + " must exist");
        }

        // 2. Verify exam_matrix_details columns
        for (String col : List.of("subject_id", "clo_id")) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'exam_matrix_details' AND column_name = ?",
                    Integer.class, col
            );
            assertEquals(1, count, "Column exam_matrix_details." + col + " must exist");
        }
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        testUser = userRepository.findAll().stream().findFirst().orElse(null);

        adminUser = userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.ADMIN).findFirst().orElse(null);
        if (adminUser == null) {
            adminUser = userRepository.save(User.builder()
                    .username("pg_admin_" + suffix)
                    .email("pg_admin_" + suffix + "@hust.edu.vn")
                    .passwordHash("hash")
                    .role(UserRole.ADMIN)
                    .build());
        }

        subjectA = subjectRepository.save(Subject.builder()
                .subjectCode("SUB_A_" + suffix)
                .subjectName("Subject A " + suffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectA.getSubjectId());

        subjectB = subjectRepository.save(Subject.builder()
                .subjectCode("SUB_B_" + suffix)
                .subjectName("Subject B " + suffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectB.getSubjectId());

        semester = semesterRepository.save(Semester.builder()
                .semesterCode("SEM_" + suffix)
                .semesterName("Semester " + suffix)
                .academicYear("2026-2027")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(4))
                .isCurrent(false)
                .build());
        createdSemesterIds.add(semester.getSemesterId());

        topicA = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .topicName("Topic A " + suffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(topicA.getTopicId());

        topicB = topicRepository.save(Topic.builder()
                .subjectId(subjectB.getSubjectId())
                .topicName("Topic B " + suffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(topicB.getTopicId());

        cloA = cloRepository.save(Clo.builder()
                .subjectId(subjectA.getSubjectId())
                .cloCode("CLO1_" + suffix)
                .description("CLO 1 for Subject A")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdCloIds.add(cloA.getCloId());

        cloB = cloRepository.save(Clo.builder()
                .subjectId(subjectB.getSubjectId())
                .cloCode("CLO1_B_" + suffix)
                .description("CLO 1 for Subject B")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdCloIds.add(cloB.getCloId());

        classA1 = classRepository.save(Class.builder()
                .subjectId(subjectA.getSubjectId())
                .semesterId(semester.getSemesterId())
                .classCode("CLA1_" + suffix)
                .maxStudents(40)
                .status(ClassStatus.ACTIVE)
                .build());
        createdClassIds.add(classA1.getClassId());

        classA2 = classRepository.save(Class.builder()
                .subjectId(subjectA.getSubjectId())
                .semesterId(semester.getSemesterId())
                .classCode("CLA2_" + suffix)
                .maxStudents(40)
                .status(ClassStatus.ACTIVE)
                .build());
        createdClassIds.add(classA2.getClassId());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdAnswerIds) {
            try { jdbcTemplate.update("DELETE FROM exam_answers WHERE answer_id = ?", id); } catch (Exception ignored) {}
        }
        createdAnswerIds.clear();

        for (UUID id : createdAttemptIds) {
            try { jdbcTemplate.update("DELETE FROM exam_attempts WHERE attempt_id = ?", id); } catch (Exception ignored) {}
        }
        createdAttemptIds.clear();

        for (UUID id : createdExamIds) {
            try {
                jdbcTemplate.update("DELETE FROM exam_questions WHERE exam_id = ?", id);
                jdbcTemplate.update("DELETE FROM exams WHERE exam_id = ?", id);
            } catch (Exception ignored) {}
        }
        createdExamIds.clear();

        for (UUID id : createdQuestionIds) {
            try { jdbcTemplate.update("DELETE FROM question_bank WHERE question_id = ?", id); } catch (Exception ignored) {}
        }
        createdQuestionIds.clear();

        for (UUID id : createdDetailIds) {
            try { jdbcTemplate.update("DELETE FROM exam_matrix_details WHERE detail_id = ?", id); } catch (Exception ignored) {}
        }
        createdDetailIds.clear();

        for (UUID id : createdMatrixIds) {
            try {
                jdbcTemplate.update("DELETE FROM exam_matrix_details WHERE matrix_id = ?", id);
                jdbcTemplate.update("DELETE FROM exam_matrix WHERE matrix_id = ?", id);
            } catch (Exception ignored) {}
        }
        createdMatrixIds.clear();

        for (UUID id : createdCloIds) {
            try { jdbcTemplate.update("DELETE FROM clos WHERE clo_id = ?", id); } catch (Exception ignored) {}
        }
        createdCloIds.clear();

        for (UUID id : createdClassIds) {
            try { jdbcTemplate.update("DELETE FROM classes WHERE class_id = ?", id); } catch (Exception ignored) {}
        }
        createdClassIds.clear();

        for (UUID id : createdTopicIds) {
            try { jdbcTemplate.update("DELETE FROM topics WHERE topic_id = ?", id); } catch (Exception ignored) {}
        }
        createdTopicIds.clear();

        for (UUID id : createdSemesterIds) {
            try { jdbcTemplate.update("DELETE FROM semesters WHERE semester_id = ?", id); } catch (Exception ignored) {}
        }
        createdSemesterIds.clear();

        for (UUID id : createdSubjectIds) {
            try { jdbcTemplate.update("DELETE FROM subjects WHERE subject_id = ?", id); } catch (Exception ignored) {}
        }
        createdSubjectIds.clear();
    }

    @Test
    @DisplayName("1. Tạo Matrix DRAFT hợp lệ")
    void test1_CreateValidDraftMatrix() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Ma trận DRAFT test")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        assertNotNull(matrix.getMatrixId());
        assertNotNull(matrix.getMatrixFamilyId());
        assertEquals(MatrixStatus.DRAFT, matrix.getStatus());
        assertFalse(matrix.getIsLocked());
        assertEquals(1, matrix.getRevisionNumber());
        assertEquals(0L, matrix.getRowVersion());
    }

    @Test
    @DisplayName("2. Mặc định isLocked = false khi tạo DRAFT")
    void test2_DefaultIsLockedIsFalse() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Ma trận mặc định lock")
                .examType(ExamType.PRACTICE)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        assertFalse(matrix.getIsLocked(), "Mặc định isLocked phải là false");
        assertEquals(MatrixStatus.DRAFT, matrix.getStatus());
    }

    @Test
    @DisplayName("3. Chặn status không hợp lệ qua CHECK constraint")
    void test3_RejectInvalidStatus() {
        UUID matrixId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO exam_matrix (matrix_id, matrix_family_id, subject_id, exam_type, status, is_locked) " +
                            "VALUES (?, ?, ?, 'FINAL', 'INVALID_STATUS', false)",
                    matrixId, familyId, subjectA.getSubjectId()
            );
        });
    }

    @Test
    @DisplayName("4. Chặn PUBLISHED nhưng isLocked = false qua CHECK constraint")
    void test4_RejectPublishedWithIsLockedFalse() {
        UUID matrixId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO exam_matrix (matrix_id, matrix_family_id, subject_id, exam_type, status, is_locked) " +
                            "VALUES (?, ?, ?, 'FINAL', 'PUBLISHED', false)",
                    matrixId, familyId, subjectA.getSubjectId()
            );
        });
    }

    @Test
    @DisplayName("5. Chặn ARCHIVED nhưng isLocked = false qua CHECK constraint")
    void test5_RejectArchivedWithIsLockedFalse() {
        UUID matrixId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO exam_matrix (matrix_id, matrix_family_id, subject_id, exam_type, status, is_locked) " +
                            "VALUES (?, ?, ?, 'FINAL', 'ARCHIVED', false)",
                    matrixId, familyId, subjectA.getSubjectId()
            );
        });
    }

    @Test
    @DisplayName("6. Lưu và đọc revisionNumber chính xác")
    void test6_SaveAndReadRevisionNumber() {
        UUID familyId = UUID.randomUUID();
        ExamMatrix rev1 = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(familyId)
                .subjectId(subjectA.getSubjectId())
                .matrixName("Ma trận Revision 1")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(rev1.getMatrixId());

        ExamMatrix rev2 = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(familyId)
                .subjectId(subjectA.getSubjectId())
                .matrixName("Ma trận Revision 2")
                .examType(ExamType.FINAL)
                .revisionNumber(2)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(rev2.getMatrixId());

        List<ExamMatrix> familyList = examMatrixRepository.findByMatrixFamilyIdOrderByRevisionNumberAsc(familyId);
        assertEquals(2, familyList.size());
        assertEquals(1, familyList.get(0).getRevisionNumber());
        assertEquals(2, familyList.get(1).getRevisionNumber());
    }

    @Test
    @DisplayName("7. rowVersion hoạt động đúng với JPA optimistic locking")
    void test7_RowVersionOptimisticLocking() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix for Locking Test")
                .examType(ExamType.MIDTERM)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());
        assertEquals(0L, matrix.getRowVersion());

        // Update matrix -> rowVersion increases
        matrix.setDescription("Updated description");
        ExamMatrix updated = examMatrixRepository.saveAndFlush(matrix);
        assertEquals(1L, updated.getRowVersion());

        // Simulate concurrent modification by trying to update with stale rowVersion
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            ExamMatrix staleMatrix = ExamMatrix.builder()
                    .matrixId(updated.getMatrixId())
                    .matrixFamilyId(updated.getMatrixFamilyId())
                    .subjectId(updated.getSubjectId())
                    .matrixName("Stale Update")
                    .examType(updated.getExamType())
                    .status(updated.getStatus())
                    .isLocked(updated.getIsLocked())
                    .revisionNumber(updated.getRevisionNumber())
                    .rowVersion(0L) // Stale version! Current is 1L
                    .build();
            examMatrixRepository.saveAndFlush(staleMatrix);
        });
    }

    @Test
    @DisplayName("8. Tạo MatrixDetail không có CLO (cloId = null)")
    void test8_CreateMatrixDetailWithoutClo() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix No CLO")
                .examType(ExamType.PRACTICE)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .cloId(null)
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(5)
                .weightPercent(BigDecimal.TEN)
                .build());
        createdDetailIds.add(detail.getDetailId());

        assertNotNull(detail.getDetailId());
        assertNull(detail.getCloId());
        assertEquals(subjectA.getSubjectId(), detail.getSubjectId());
    }

    @Test
    @DisplayName("9. Tạo MatrixDetail với CLO cùng Subject")
    void test9_CreateMatrixDetailWithMatchingSubjectClo() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix with Matching CLO")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .cloId(cloA.getCloId())
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .numQuestions(10)
                .weightPercent(BigDecimal.valueOf(20))
                .build());
        createdDetailIds.add(detail.getDetailId());

        assertNotNull(detail.getDetailId());
        assertEquals(cloA.getCloId(), detail.getCloId());
    }

    @Test
    @DisplayName("10. Chặn MatrixDetail có CLO khác Subject")
    void test10_RejectMatrixDetailWithDifferentSubjectClo() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix SubA")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        // cloB belongs to subjectB, but matrix and detail belong to subjectA
        ExamMatrixDetail invalidDetail = ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .cloId(cloB.getCloId()) // Mismatch!
                .difficultyLevel(DifficultyLevel.HARD)
                .numQuestions(5)
                .weightPercent(BigDecimal.valueOf(10))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixDetailRepository.saveAndFlush(invalidDetail);
        }, "Phải bị chặn bởi fk_matrix_details_clo_subject khi CLO khác Subject");
    }

    @Test
    @DisplayName("11. Chặn MatrixDetail có Topic khác Subject")
    void test11_RejectMatrixDetailWithDifferentSubjectTopic() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix SubA")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        // topicB belongs to subjectB, but detail has subjectId = subjectA
        ExamMatrixDetail invalidDetail = ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicB.getTopicId()) // Mismatch!
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(5)
                .weightPercent(BigDecimal.valueOf(10))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixDetailRepository.saveAndFlush(invalidDetail);
        }, "Phải bị chặn bởi fk_matrix_details_topic_subject khi Topic khác Subject");
    }

    @Test
    @DisplayName("12. Chặn MatrixDetail có subjectId khác Matrix.subjectId")
    void test12_RejectMatrixDetailWithDifferentSubjectMatrix() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix SubA")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        // detail sets subjectId = subjectB, but matrix has subjectId = subjectA
        ExamMatrixDetail invalidDetail = ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectB.getSubjectId()) // Mismatch with Matrix!
                .topicId(topicB.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(5)
                .weightPercent(BigDecimal.valueOf(10))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixDetailRepository.saveAndFlush(invalidDetail);
        }, "Phải bị chặn bởi fk_matrix_details_matrix_subject khi detail.subjectId khác matrix.subjectId");
    }

    @Test
    @DisplayName("13. Không thể xóa CLO đang được MatrixDetail tham chiếu (ON DELETE RESTRICT)")
    void test13_RestrictDeleteCloReferencedByMatrixDetail() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix referencing CLO")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .cloId(cloA.getCloId())
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .numQuestions(5)
                .weightPercent(BigDecimal.TEN)
                .build());
        createdDetailIds.add(detail.getDetailId());

        assertThrows(DataIntegrityViolationException.class, () -> {
            cloRepository.delete(cloA);
            cloRepository.flush();
        }, "Phải chặn xóa CLO do fk_matrix_details_clo_subject ON DELETE RESTRICT");
    }

    @Test
    @DisplayName("14. Không thể xóa Topic đang được MatrixDetail tham chiếu theo chính sách mới (ON DELETE RESTRICT)")
    void test14_RestrictDeleteTopicReferencedByMatrixDetail() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix referencing Topic")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(5)
                .weightPercent(BigDecimal.TEN)
                .build());
        createdDetailIds.add(detail.getDetailId());

        assertThrows(DataIntegrityViolationException.class, () -> {
            topicRepository.delete(topicA);
            topicRepository.flush();
        }, "Phải chặn xóa Topic do fk_matrix_details_topic_subject ON DELETE RESTRICT");
    }

    @Test
    @DisplayName("15. Không thể xóa Matrix đang được Exam tham chiếu")
    void test15_RestrictDeleteMatrixReferencedByExam() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix referenced by Exam")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        Exam exam = examRepository.save(Exam.builder()
                .classId(classA1.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Kỳ thi Cuối kỳ Lớp A1")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(exam.getExamId());

        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixRepository.delete(matrix);
            examMatrixRepository.flush();
        }, "Không thể xóa ExamMatrix khi đang được Exam tham chiếu");
    }

    @Test
    @DisplayName("16. Hai Exam độc lập của hai Class cùng Subject có thể tham chiếu một Matrix")
    void test16_MultipleClassExamsCanReferenceSameMatrix() {
        ExamMatrix commonMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Common Matrix Subject A")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(commonMatrix.getMatrixId());

        Exam examClass1 = examRepository.save(Exam.builder()
                .classId(classA1.getClassId())
                .matrixId(commonMatrix.getMatrixId())
                .title("Thi Cuối kỳ Lớp A1")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(examClass1.getExamId());

        Exam examClass2 = examRepository.save(Exam.builder()
                .classId(classA2.getClassId())
                .matrixId(commonMatrix.getMatrixId())
                .title("Thi Cuối kỳ Lớp A2")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(examClass2.getExamId());

        assertEquals(commonMatrix.getMatrixId(), examClass1.getMatrixId());
        assertEquals(commonMatrix.getMatrixId(), examClass2.getMatrixId());
        assertNotEquals(examClass1.getClassId(), examClass2.getClassId());
    }

    @Test
    @DisplayName("17. Hai Exam thuộc Class khác nhau vẫn giữ examId riêng")
    void test17_TwoExamsFromDifferentClassesRetainDistinctIds() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix Isolation Test")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        Exam exam1 = examRepository.save(Exam.builder()
                .classId(classA1.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Exam 1")
                .examType(ExamType.FINAL)
                .durationMinutes(50)
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(exam1.getExamId());

        Exam exam2 = examRepository.save(Exam.builder()
                .classId(classA2.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Exam 2")
                .examType(ExamType.FINAL)
                .durationMinutes(50)
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(exam2.getExamId());

        assertNotEquals(exam1.getExamId(), exam2.getExamId(), "Mỗi Exam phải có examId riêng biệt");
    }

    @Test
    @DisplayName("18. Không làm mất ExamQuestion, ExamAttempt và ExamAnswer lịch sử")
    void test18_HistoricalExamQuestionAttemptAnswerPreserved() {
        Exam exam = examRepository.save(Exam.builder()
                .classId(classA1.getClassId())
                .title("Historical Exam")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .createdBy(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .createdAt(Instant.now())
                .build());
        createdExamIds.add(exam.getExamId());

        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Lịch sử question")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());
        createdQuestionIds.add(question.getQuestionId());

        ExamQuestion eq = examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(question.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.ONE)
                .build());

        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(exam.getExamId())
                .studentId(testUser != null ? testUser.getUserId() : UUID.randomUUID())
                .attemptNumber(1)
                .startedAt(Instant.now())
                .status(AttemptStatus.SUBMITTED)
                .totalScore(BigDecimal.TEN)
                .build());
        createdAttemptIds.add(attempt.getAttemptId());

        ExamAnswer answer = examAnswerRepository.save(ExamAnswer.builder()
                .attemptId(attempt.getAttemptId())
                .questionId(question.getQuestionId())
                .isCorrect(true)
                .score(BigDecimal.TEN)
                .build());
        createdAnswerIds.add(answer.getAnswerId());

        assertTrue(examQuestionRepository.existsByExamIdAndQuestionId(exam.getExamId(), question.getQuestionId()));
        assertTrue(examAttemptRepository.existsById(attempt.getAttemptId()));
        assertTrue(examAnswerRepository.existsById(answer.getAnswerId()));
    }

    @Test
    @DisplayName("19. Migration giữ nguyên MatrixDetail cũ với cloId NULL")
    void test19_LegacyDetailsWithNullCloRemainValid() {
        ExamMatrix legacyMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Legacy Matrix")
                .examType(ExamType.PRACTICE)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(legacyMatrix.getMatrixId());

        ExamMatrixDetail legacyDetail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(legacyMatrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .cloId(null)
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(10)
                .weightPercent(BigDecimal.valueOf(100))
                .build());
        createdDetailIds.add(legacyDetail.getDetailId());

        Optional<ExamMatrixDetail> found = examMatrixDetailRepository.findById(legacyDetail.getDetailId());
        assertTrue(found.isPresent());
        assertNull(found.get().getCloId(), "cloId vẫn là null cho bản ghi theo chuẩn cũ");
    }

    @Test
    @DisplayName("20. Kiểm tra các constraint bằng pg_catalog")
    void test20_VerifyConstraintsViaPgCatalog() {
        List<String> requiredConstraints = List.of(
                "chk_exam_matrix_status",
                "chk_exam_matrix_status_locked",
                "chk_exam_matrix_revision_number",
                "chk_exam_matrix_row_version",
                "uq_matrix_family_revision",
                "uq_exam_matrix_id_subject",
                "fk_matrix_details_matrix_subject",
                "fk_matrix_details_topic_subject",
                "fk_matrix_details_clo_subject"
        );

        for (String conName : requiredConstraints) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = ?",
                    Integer.class, conName
            );
            assertEquals(1, exists, "Constraint '" + conName + "' phải tồn tại trong PostgreSQL catalog");
        }
    }

    @Test
    @DisplayName("21. FK composite Matrix-Subject sử dụng RESTRICT (pg_catalog)")
    void test21_FkMatrixDetailsMatrixSubjectIsRestrict() {
        String rule = jdbcTemplate.queryForObject(
                "SELECT confdeltype::text FROM pg_constraint WHERE conrelid = 'exam_matrix_details'::regclass AND conname = 'fk_matrix_details_matrix_subject'",
                String.class
        );
        assertEquals("r", rule, "fk_matrix_details_matrix_subject phải có delete rule là RESTRICT ('r')");
    }

    @Test
    @DisplayName("22. Không còn bất kỳ FK nào từ exam_matrix_details sang exam_matrix có CASCADE")
    void test22_NoCascadeForeignKeyFromDetailsToMatrix() {
        Integer cascadeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'exam_matrix_details'::regclass AND confrelid = 'exam_matrix'::regclass AND confdeltype = 'c'",
                Integer.class
        );
        assertEquals(0, cascadeCount, "Không được còn FK nào có ON DELETE CASCADE từ exam_matrix_details sang exam_matrix");
    }

    @Test
    @DisplayName("23. Xóa Matrix trực tiếp bị DB từ chối khi còn MatrixDetail (ON DELETE RESTRICT)")
    void test23_CannotDeleteMatrixDirectlyWhenDetailsExist() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix with Detail Test RESTRICT")
                .examType(ExamType.PRACTICE)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(5)
                .weightPercent(BigDecimal.TEN)
                .build());
        createdDetailIds.add(detail.getDetailId());

        // Cố xóa exam_matrix trực tiếp mà không xóa detail
        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixRepository.delete(matrix);
            examMatrixRepository.flush();
        }, "Database phải từ chối xóa Matrix khi còn MatrixDetail do ON DELETE RESTRICT");
    }

    @Test
    @DisplayName("24. DRAFT Matrix có thể xóa thành công khi xóa detail chủ động trong transaction (Service)")
    void test24_CanDeleteDraftMatrixWhenDetailsDeletedExplicitlyFirst() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("DRAFT Matrix to Delete")
                .examType(ExamType.PRACTICE)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        UUID matrixId = matrix.getMatrixId();

        ExamMatrixDetail detail = examMatrixDetailRepository.save(ExamMatrixDetail.builder()
                .matrixId(matrixId)
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .difficultyLevel(DifficultyLevel.EASY)
                .numQuestions(3)
                .weightPercent(BigDecimal.TEN)
                .build());
        UUID detailId = detail.getDetailId();

        // Xóa thông qua Service (Service xóa details trước rồi xóa matrix trong cùng 1 transaction)
        assertDoesNotThrow(() -> {
            examMatrixService.deleteExamMatrix(matrixId, testUser != null ? testUser.getUserId() : UUID.randomUUID());
        });

        assertFalse(examMatrixRepository.existsById(matrixId), "Matrix DRAFT phải bị xóa");
        assertFalse(examMatrixDetailRepository.existsById(detailId), "MatrixDetail phải bị xóa chủ động");
    }

    @Test
    @DisplayName("25. Service chặn xóa Matrix đã PUBLISHED hoặc ARCHIVED")
    void test25_CannotDeletePublishedOrArchivedMatrixViaService() {
        ExamMatrix publishedMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Published Matrix")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(publishedMatrix.getMatrixId());

        CustomException exPublished = assertThrows(CustomException.class, () -> {
            examMatrixService.deleteExamMatrix(publishedMatrix.getMatrixId(), adminUser.getUserId());
        });
        assertTrue(exPublished.getMessage().contains("đã khóa"), "Phải báo lỗi khi xóa ma trận PUBLISHED");

        ExamMatrix archivedMatrix = examMatrixRepository.save(ExamMatrix.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("Archived Matrix")
                .examType(ExamType.FINAL)
                .status(MatrixStatus.ARCHIVED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(archivedMatrix.getMatrixId());

        CustomException exArchived = assertThrows(CustomException.class, () -> {
            examMatrixService.deleteExamMatrix(archivedMatrix.getMatrixId(), adminUser.getUserId());
        });
        assertTrue(exArchived.getMessage().contains("đã khóa"), "Phải báo lỗi khi xóa ma trận ARCHIVED");
    }

    @Test
    @DisplayName("26. Neon PostgreSQL: Chặn trùng lặp (matrixFamilyId, revisionNumber) bằng unique constraint uq_matrix_family_revision")
    void test26_ConcurrentRevisionClone_EnforcesUniqueFamilyRevisionConstraint() {
        UUID familyId = UUID.randomUUID();
        ExamMatrix m1 = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(familyId)
                .subjectId(subjectA.getSubjectId())
                .matrixName("Family Revision 1")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(m1.getMatrixId());

        // Attempting to insert duplicate revision 1 for same familyId
        ExamMatrix mDuplicate = ExamMatrix.builder()
                .matrixFamilyId(familyId)
                .subjectId(subjectA.getSubjectId())
                .matrixName("Duplicate Family Revision 1")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            examMatrixRepository.saveAndFlush(mDuplicate);
        }, "Phải ném DataIntegrityViolationException do uq_matrix_family_revision");
    }

    @Test
    @DisplayName("27. Neon PostgreSQL: Xung đột Optimistic Locking khi update đồng thời trên ExamMatrix (@Version)")
    void test27_OptimisticLockingOnConcurrentUpdate_ThrowsOptimisticLockException() {
        ExamMatrix m = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(UUID.randomUUID())
                .subjectId(subjectA.getSubjectId())
                .matrixName("Original Name")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .build());
        createdMatrixIds.add(m.getMatrixId());

        // Load 2 separate entity copies
        ExamMatrix copy1 = examMatrixRepository.findById(m.getMatrixId()).orElseThrow();
        ExamMatrix copy2 = examMatrixRepository.findById(m.getMatrixId()).orElseThrow();

        // Tx1 saves and bumps row_version
        copy1.setMatrixName("Updated by Tx1");
        examMatrixRepository.saveAndFlush(copy1);

        // Tx2 tries to save stale version
        copy2.setMatrixName("Updated by Tx2");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            examMatrixRepository.saveAndFlush(copy2);
        }, "Phải ném ObjectOptimisticLockingFailureException khi phiên bản entity bị cũ");
    }

    @Test
    @DisplayName("28. Neon PostgreSQL: Bulk-provision FINAL tạo các bài thi độc lập cho nhiều lớp")
    void test28_BulkProvisionFinalExams_CreatesDistinctExamsForClassesOnPostgreSQL() {
        // Seed approved question
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .content("Câu hỏi PG Final " + UUID.randomUUID())
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(adminUser.getUserId())
                .build());
        createdQuestionIds.add(q.getQuestionId());

        // Create published matrix
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(subjectA.getSubjectId())
                .matrixName("PG Final Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicA.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .weightPercent(BigDecimal.ONE)
                                .build()
                ))
                .build();
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        createdMatrixIds.add(draft.getMatrixId());
        ExamMatrixDTO published = examMatrixService.publishMatrix(draft.getMatrixId(), adminUser.getUserId());

        BulkProvisionFinalExamDTO bpDto = BulkProvisionFinalExamDTO.builder()
                .matrixId(published.getMatrixId())
                .classIds(List.of(classA1.getClassId(), classA2.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .semesterId(semester.getSemesterId())
                .autoGenerateQuestions(true)
                .build();

        BulkProvisionFinalExamResultDTO result = examService.bulkProvisionFinalExams(bpDto, adminUser.getUserId());
        assertNotNull(result);
        assertEquals(2, result.getTotalClassesProvisioned());

        UUID examId1 = result.getCreatedExams().get(0).getExamId();
        UUID examId2 = result.getCreatedExams().get(1).getExamId();
        createdExamIds.add(examId1);
        createdExamIds.add(examId2);

        assertNotEquals(examId1, examId2);
        assertTrue(examRepository.existsById(examId1));
        assertTrue(examRepository.existsById(examId2));
    }

    @Test
    @DisplayName("29. Neon PostgreSQL: ARCHIVE ma trận không làm ảnh hưởng đến Exam/Attempt/Answer lịch sử")
    void test29_ArchiveMatrix_DoesNotAffectHistoricalExamsAttemptsAnswers() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(UUID.randomUUID())
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix to Archive with History")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        Exam historicalExam = examRepository.save(Exam.builder()
                .classId(classA1.getClassId())
                .matrixId(matrix.getMatrixId())
                .title("Historical Final Exam")
                .examType(ExamType.FINAL)
                .durationMinutes(60)
                .startTime(Instant.now().minusSeconds(7200))
                .endTime(Instant.now().minusSeconds(3600))
                .createdBy(adminUser.getUserId())
                .createdAt(Instant.now().minusSeconds(7200))
                .build());
        createdExamIds.add(historicalExam.getExamId());

        ExamAttempt attempt = examAttemptRepository.save(ExamAttempt.builder()
                .examId(historicalExam.getExamId())
                .studentId(adminUser.getUserId())
                .attemptNumber(1)
                .status(AttemptStatus.SUBMITTED)
                .startedAt(Instant.now().minusSeconds(7000))
                .submittedAt(Instant.now().minusSeconds(4000))
                .totalScore(BigDecimal.valueOf(8.5))
                .build());
        createdAttemptIds.add(attempt.getAttemptId());

        // Archive matrix
        ExamMatrixDTO archived = examMatrixService.archiveMatrix(matrix.getMatrixId(), adminUser.getUserId());
        assertEquals(MatrixStatus.ARCHIVED, archived.getMatrixStatus());

        // Historical records remain fully intact and readable
        Exam examAfterArchive = examRepository.findById(historicalExam.getExamId()).orElseThrow();
        assertEquals(matrix.getMatrixId(), examAfterArchive.getMatrixId());
        assertEquals("Historical Final Exam", examAfterArchive.getTitle());

        ExamAttempt attemptAfterArchive = examAttemptRepository.findById(attempt.getAttemptId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(8.5).compareTo(attemptAfterArchive.getTotalScore()));
        assertEquals(AttemptStatus.SUBMITTED, attemptAfterArchive.getStatus());
    }

    @Test
    @DisplayName("30. Neon PostgreSQL: Bulk-provision từ chối lớp học đã có bài thi FINAL với ma trận này (409 Conflict)")
    void test30_BulkProvisionDuplicateClassAndMatrix_RejectedOnPostgreSQL() {
        ExamMatrix matrix = examMatrixRepository.save(ExamMatrix.builder()
                .matrixFamilyId(UUID.randomUUID())
                .subjectId(subjectA.getSubjectId())
                .matrixName("Matrix for Duplicate Check")
                .examType(ExamType.FINAL)
                .revisionNumber(1)
                .status(MatrixStatus.PUBLISHED)
                .isLocked(true)
                .build());
        createdMatrixIds.add(matrix.getMatrixId());

        BulkProvisionFinalExamDTO bpDto = BulkProvisionFinalExamDTO.builder()
                .matrixId(matrix.getMatrixId())
                .classIds(List.of(classA1.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200))
                .semesterId(semester.getSemesterId())
                .autoGenerateQuestions(false)
                .build();

        // 1st provision
        BulkProvisionFinalExamResultDTO r1 = examService.bulkProvisionFinalExams(bpDto, adminUser.getUserId());
        createdExamIds.add(r1.getCreatedExams().get(0).getExamId());

        // 2nd provision for same class and matrix -> throws Conflict
        CustomException ex = assertThrows(CustomException.class, () -> {
            examService.bulkProvisionFinalExams(bpDto, adminUser.getUserId());
        });
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }
}
