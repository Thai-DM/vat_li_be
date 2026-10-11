package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.QuestionApproval;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.QuestionCloId;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IQuestionApprovalRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
public class PostgreSqlCloIntegrityIntegrationTest {

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @Autowired
    private IQuestionApprovalRepository questionApprovalRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Subject subjectPhys;
    private Subject subjectMath;
    private Topic topicPhys;
    private Topic topicMath;
    private User instructor;
    private User reviewer;

    private final List<UUID> createdCloIds = new ArrayList<>();
    private final List<QuestionCloId> createdQuestionCloIds = new ArrayList<>();
    private final List<UUID> createdApprovalIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdTopicIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeAll
    void verifySchema() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('clos', 'question_clos', 'question_approvals')",
                Integer.class
        );
        assertEquals(3, count, "Tables clos, question_clos, question_approvals must exist in public schema");
    }

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        subjectPhys = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_" + testSuffix)
                .subjectName("Vat ly CLO Test " + testSuffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectPhys.getSubjectId());

        subjectMath = subjectRepository.save(Subject.builder()
                .subjectCode("MATH_" + testSuffix)
                .subjectName("Toan CLO Test " + testSuffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectMath.getSubjectId());

        topicPhys = topicRepository.save(Topic.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicName("Dong hoc chat diem " + testSuffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(topicPhys.getTopicId());

        topicMath = topicRepository.save(Topic.builder()
                .subjectId(subjectMath.getSubjectId())
                .topicName("Giai tich 1 " + testSuffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(topicMath.getTopicId());

        instructor = userRepository.save(User.builder()
                .username("inst_" + testSuffix)
                .email("inst_" + testSuffix + "@test.edu.vn")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .role(UserRole.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(instructor.getUserId());

        reviewer = userRepository.save(User.builder()
                .username("rev_" + testSuffix)
                .email("rev_" + testSuffix + "@test.edu.vn")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(reviewer.getUserId());
    }

    @AfterEach
    void tearDown() {
        for (QuestionCloId id : createdQuestionCloIds) {
            try {
                jdbcTemplate.update("DELETE FROM question_clos WHERE question_id = ? AND clo_id = ?",
                        id.getQuestionId(), id.getCloId());
            } catch (Exception ignored) {}
        }
        for (UUID approvalId : createdApprovalIds) {
            try {
                questionApprovalRepository.deleteById(approvalId);
            } catch (Exception ignored) {}
        }
        for (UUID questionId : createdQuestionIds) {
            try {
                jdbcTemplate.update("DELETE FROM question_options WHERE question_id = ?", questionId);
                questionBankRepository.deleteById(questionId);
            } catch (Exception ignored) {}
        }
        for (UUID cloId : createdCloIds) {
            try {
                cloRepository.deleteById(cloId);
            } catch (Exception ignored) {}
        }
        for (UUID topicId : createdTopicIds) {
            try {
                topicRepository.deleteById(topicId);
            } catch (Exception ignored) {}
        }
        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {}
        }
        for (UUID subjectId : createdSubjectIds) {
            try {
                subjectRepository.deleteById(subjectId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("CLO-01: Tạo CLO hợp lệ với đầy đủ metadata và timestamp")
    void test01_CreateValidClo_Success() {
        Clo clo = Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO1")
                .description("Hieu ro va van dung cac dinh luat Newton ve dong luc hoc")
                .orderIndex(1)
                .isActive(true)
                .build();
        Clo saved = cloRepository.save(clo);
        createdCloIds.add(saved.getCloId());

        assertNotNull(saved.getCloId());
        assertEquals("CLO1", saved.getCloCode());
        assertEquals(subjectPhys.getSubjectId(), saved.getSubjectId());
        assertTrue(saved.getIsActive());
        assertEquals(1, saved.getOrderIndex());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("CLO-02: Chặn trùng clo_code trong cùng Subject (uq_clos_subject_code)")
    void test02_PreventDuplicateCloCodeInSameSubject() {
        Clo clo1 = Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO1")
                .description("Mo ta CLO1")
                .orderIndex(1)
                .build();
        Clo saved1 = cloRepository.save(clo1);
        createdCloIds.add(saved1.getCloId());

        Clo clo2 = Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO1")
                .description("Mo ta trung ma CLO1")
                .orderIndex(2)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            cloRepository.saveAndFlush(clo2);
        });
    }

    @Test
    @DisplayName("CLO-03: Cho phép cùng clo_code ở hai Subject khác nhau")
    void test03_AllowSameCloCodeInDifferentSubjects() {
        Clo cloPhys = Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO1")
                .description("CLO1 Mon Vat ly")
                .orderIndex(1)
                .build();
        Clo savedPhys = cloRepository.save(cloPhys);
        createdCloIds.add(savedPhys.getCloId());

        Clo cloMath = Clo.builder()
                .subjectId(subjectMath.getSubjectId())
                .cloCode("CLO1")
                .description("CLO1 Mon Toan")
                .orderIndex(1)
                .build();
        Clo savedMath = cloRepository.save(cloMath);
        createdCloIds.add(savedMath.getCloId());

        assertNotNull(savedPhys.getCloId());
        assertNotNull(savedMath.getCloId());
        assertNotEquals(savedPhys.getCloId(), savedMath.getCloId());
        assertEquals("CLO1", savedPhys.getCloCode());
        assertEquals("CLO1", savedMath.getCloCode());
    }

    @Test
    @DisplayName("CLO-04: Liên kết Question và CLO cùng môn thành công")
    void test04_LinkQuestionAndClo_SameSubject_Success() {
        Clo clo = cloRepository.save(Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO1")
                .description("CLO Vat ly")
                .orderIndex(1)
                .build());
        createdCloIds.add(clo.getCloId());

        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Van toc chuyen dong thang deu la hang so?")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructor.getUserId())
                .build());
        createdQuestionIds.add(question.getQuestionId());

        QuestionClo link = QuestionClo.builder()
                .questionId(question.getQuestionId())
                .cloId(clo.getCloId())
                .subjectId(subjectPhys.getSubjectId())
                .build();
        QuestionClo savedLink = questionCloRepository.save(link);
        createdQuestionCloIds.add(new QuestionCloId(savedLink.getQuestionId(), savedLink.getCloId()));

        assertNotNull(savedLink.getAssignedAt());
        assertTrue(questionCloRepository.existsByQuestionIdAndCloId(question.getQuestionId(), clo.getCloId()));
    }

    @Test
    @DisplayName("CLO-05: PostgreSQL từ chối Question-CLO khác Subject bằng Composite Foreign Key")
    void test05_PreventQuestionCloLinkWithDifferentSubjects() {
        Clo cloMath = cloRepository.save(Clo.builder()
                .subjectId(subjectMath.getSubjectId())
                .cloCode("CLO1")
                .description("CLO Toan")
                .orderIndex(1)
                .build());
        createdCloIds.add(cloMath.getCloId());

        QuestionBank questionPhys = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cau hoi vat ly khong the map vao CLO toan")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructor.getUserId())
                .build());
        createdQuestionIds.add(questionPhys.getQuestionId());

        // Thu insert truc tiep vao question_clos voi questionPhys (thuoc subjectPhys) nhung cloMath (thuoc subjectMath)
        // 1. Neu de subjectId = subjectPhys -> vi pham FK (clo_id, subject_id) -> clos(clo_id, subject_id)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO question_clos (question_id, clo_id, subject_id) VALUES (?, ?, ?)",
                    questionPhys.getQuestionId(), cloMath.getCloId(), subjectPhys.getSubjectId()
            );
        });

        // 2. Neu de subjectId = subjectMath -> vi pham FK (question_id, subject_id) -> question_bank(question_id, subject_id)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO question_clos (question_id, clo_id, subject_id) VALUES (?, ?, ?)",
                    questionPhys.getQuestionId(), cloMath.getCloId(), subjectMath.getSubjectId()
            );
        });
    }

    @Test
    @DisplayName("CLO-06: FK bảo vệ dữ liệu khi cố xóa CLO đang được liên kết (ON DELETE RESTRICT)")
    void test06_FkProtectsCloFromDeletionWhenReferenced() {
        Clo clo = cloRepository.save(Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO2")
                .description("CLO Vat ly 2")
                .orderIndex(2)
                .build());
        createdCloIds.add(clo.getCloId());

        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cau hoi thu 2")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructor.getUserId())
                .build());
        createdQuestionIds.add(question.getQuestionId());

        QuestionClo link = QuestionClo.builder()
                .questionId(question.getQuestionId())
                .cloId(clo.getCloId())
                .subjectId(subjectPhys.getSubjectId())
                .build();
        questionCloRepository.save(link);
        createdQuestionCloIds.add(new QuestionCloId(question.getQuestionId(), clo.getCloId()));

        // Co xoa CLO khi dang co question_clos tham chieu toi
        assertThrows(DataIntegrityViolationException.class, () -> {
            cloRepository.deleteById(clo.getCloId());
            cloRepository.flush();
        });
    }

    @Test
    @DisplayName("CLO-07: Tạo QuestionApproval hợp lệ cho Audit Trail")
    void test07_CreateQuestionApprovalAudit_Success() {
        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cau hoi kiem thu audit trail")
                .difficultyLevel(DifficultyLevel.HARD)
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructor.getUserId())
                .build());
        createdQuestionIds.add(question.getQuestionId());

        QuestionApproval audit = QuestionApproval.builder()
                .questionId(question.getQuestionId())
                .actorId(reviewer.getUserId())
                .action("APPROVE")
                .fromStatus("PENDING")
                .toStatus("APPROVED")
                .comment("Cau hoi dat chuan ve noi dung va do kho")
                .build();
        QuestionApproval savedAudit = questionApprovalRepository.save(audit);
        createdApprovalIds.add(savedAudit.getApprovalId());

        assertNotNull(savedAudit.getApprovalId());
        assertEquals("APPROVE", savedAudit.getAction());
        assertEquals("APPROVED", savedAudit.getToStatus());
        assertNotNull(savedAudit.getCreatedAt());

        List<QuestionApproval> history = questionApprovalRepository.findByQuestionIdOrderByCreatedAtDesc(question.getQuestionId());
        assertEquals(1, history.size());
        assertEquals("APPROVE", history.get(0).getAction());
    }

    @Test
    @DisplayName("CLO-08: FK bảo vệ lịch sử phê duyệt khi cố xóa câu hỏi (ON DELETE RESTRICT)")
    void test08_FkProtectsApprovalHistoryFromDeletion() {
        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cau hoi co audit log")
                .difficultyLevel(DifficultyLevel.HARD)
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructor.getUserId())
                .build());
        createdQuestionIds.add(question.getQuestionId());

        QuestionApproval audit = QuestionApproval.builder()
                .questionId(question.getQuestionId())
                .actorId(reviewer.getUserId())
                .action("SUBMIT")
                .fromStatus("DRAFT")
                .toStatus("PENDING")
                .comment("Giang vien gui duyet cau hoi")
                .build();
        QuestionApproval savedAudit = questionApprovalRepository.save(audit);
        createdApprovalIds.add(savedAudit.getApprovalId());

        // Co xoa Question khi dang co QuestionApproval tham chieu -> database tu choi
        assertThrows(DataIntegrityViolationException.class, () -> {
            questionBankRepository.deleteById(question.getQuestionId());
            questionBankRepository.flush();
        });
    }

    @Test
    @DisplayName("CLO-09: Migration không thay đổi 8 câu hỏi PENDING hiện tại trên Neon")
    void test09_MigrationMaintainsExistingPendingQuestions() {
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE approval_status = 'PENDING' OR approval_status::text = 'pending'",
                Integer.class
        );
        assertNotNull(pendingCount);
        assertTrue(pendingCount >= 8, "8 cau hoi goc phai duoc bao toan nguyen ven o trang thai PENDING");
    }

    @Test
    @DisplayName("CLO-10: Kiểm tra JPA Entity mapping và các trường Audit mới trên PostgreSQL Neon")
    void test10_JpaEntityMapping_FullCycle() {
        QuestionBank question = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicId(topicPhys.getTopicId())
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Cau hoi kiem tra audit fields")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .approvalStatus(ApprovalStatus.REJECTED)
                .createdBy(instructor.getUserId())
                .reviewedBy(reviewer.getUserId())
                .reviewedAt(Instant.now())
                .rejectionReason("Thieu hinh anh minh hoa thi nghiem")
                .build());
        createdQuestionIds.add(question.getQuestionId());

        Optional<QuestionBank> retrieved = questionBankRepository.findById(question.getQuestionId());
        assertTrue(retrieved.isPresent());
        QuestionBank q = retrieved.get();
        assertEquals(reviewer.getUserId(), q.getReviewedBy());
        assertNotNull(q.getReviewedAt());
        assertEquals("Thieu hinh anh minh hoa thi nghiem", q.getRejectionReason());
        assertEquals(ApprovalStatus.REJECTED, q.getApprovalStatus());
    }
}
