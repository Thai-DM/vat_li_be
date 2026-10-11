package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
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
public class PostgreSqlScopedMaterialIntegrityIntegrationTest {

    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Subject subjectA;
    private Subject subjectB;
    private Semester semester;
    private Topic topicA;
    private Topic topicB;
    private Class classA;
    private Class classB;

    private final List<UUID> createdMaterialIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdTopicIds = new ArrayList<>();
    private final List<UUID> createdSemesterIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    @BeforeAll
    void verifySchema() {
        // 1. Verify learning_materials table exists and has new columns
        Integer subjectIdCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'learning_materials' AND column_name = 'subject_id'",
                Integer.class
        );
        assertEquals(1, subjectIdCol, "Column learning_materials.subject_id must exist");

        Integer scopeCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'learning_materials' AND column_name = 'scope'",
                Integer.class
        );
        assertEquals(1, scopeCol, "Column learning_materials.scope must exist");

        Integer classIdCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'learning_materials' AND column_name = 'class_id'",
                Integer.class
        );
        assertEquals(1, classIdCol, "Column learning_materials.class_id must exist");

        // 2. Verify CHECK constraints
        Integer chkScope = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'chk_learning_materials_scope'",
                Integer.class
        );
        assertEquals(1, chkScope, "CHECK constraint chk_learning_materials_scope must exist");

        Integer chkScopeClass = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'chk_learning_materials_scope_class'",
                Integer.class
        );
        assertEquals(1, chkScopeClass, "CHECK constraint chk_learning_materials_scope_class must exist");

        // 3. Verify Composite Foreign Keys
        Integer fkTopicSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'fk_materials_topic_subject'",
                Integer.class
        );
        assertEquals(1, fkTopicSubject, "Composite FK fk_materials_topic_subject must exist");

        Integer fkClassSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'fk_materials_class_subject'",
                Integer.class
        );
        assertEquals(1, fkClassSubject, "Composite FK fk_materials_class_subject must exist");

        // 4. Verify Unique constraints on topics and classes
        Integer uqTopicSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'uq_topics_id_subject'",
                Integer.class
        );
        assertEquals(1, uqTopicSubject, "Unique constraint uq_topics_id_subject must exist");

        Integer uqClassSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'uq_classes_id_subject'",
                Integer.class
        );
        assertEquals(1, uqClassSubject, "Unique constraint uq_classes_id_subject must exist");
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        subjectA = subjectRepository.save(Subject.builder()
                .subjectCode("SUBA_" + suffix)
                .subjectName("Subject A " + suffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectA.getSubjectId());

        subjectB = subjectRepository.save(Subject.builder()
                .subjectCode("SUBB_" + suffix)
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
                .topicName("Topic A in Subject A " + suffix)
                .orderIndex(1)
                .description("Topic in Subject A")
                .build());
        createdTopicIds.add(topicA.getTopicId());

        topicB = topicRepository.save(Topic.builder()
                .subjectId(subjectB.getSubjectId())
                .topicName("Topic B in Subject B " + suffix)
                .orderIndex(1)
                .description("Topic in Subject B")
                .build());
        createdTopicIds.add(topicB.getTopicId());

        classA = classRepository.save(Class.builder()
                .subjectId(subjectA.getSubjectId())
                .semesterId(semester.getSemesterId())
                .classCode("CLA_" + suffix)
                .maxStudents(40)
                .status(ClassStatus.ACTIVE)
                .build());
        createdClassIds.add(classA.getClassId());

        classB = classRepository.save(Class.builder()
                .subjectId(subjectB.getSubjectId())
                .semesterId(semester.getSemesterId())
                .classCode("CLB_" + suffix)
                .maxStudents(40)
                .status(ClassStatus.ACTIVE)
                .build());
        createdClassIds.add(classB.getClassId());
    }

    @AfterEach
    void tearDown() {
        for (UUID materialId : createdMaterialIds) {
            try {
                jdbcTemplate.update("DELETE FROM learning_materials WHERE material_id = ?", materialId);
            } catch (Exception ignored) {
            }
        }
        createdMaterialIds.clear();

        for (UUID classId : createdClassIds) {
            try {
                jdbcTemplate.update("DELETE FROM classes WHERE class_id = ?", classId);
            } catch (Exception ignored) {
            }
        }
        createdClassIds.clear();

        for (UUID topicId : createdTopicIds) {
            try {
                jdbcTemplate.update("DELETE FROM topics WHERE topic_id = ?", topicId);
            } catch (Exception ignored) {
            }
        }
        createdTopicIds.clear();

        for (UUID semesterId : createdSemesterIds) {
            try {
                jdbcTemplate.update("DELETE FROM semesters WHERE semester_id = ?", semesterId);
            } catch (Exception ignored) {
            }
        }
        createdSemesterIds.clear();

        for (UUID subjectId : createdSubjectIds) {
            try {
                jdbcTemplate.update("DELETE FROM subjects WHERE subject_id = ?", subjectId);
            } catch (Exception ignored) {
            }
        }
        createdSubjectIds.clear();
    }

    @Test
    @DisplayName("1. Tạo SHARED Material hợp lệ (scope=SHARED, class_id=NULL)")
    void test1_ValidSharedMaterialCreation() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.SHARED)
                .classId(null)
                .title("Shared Material Valid 1")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .fileUrl("https://example.com/shared1.pdf")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        assertNotNull(saved.getMaterialId(), "Material ID must be generated");
        createdMaterialIds.add(saved.getMaterialId());

        assertEquals(MaterialScope.SHARED, saved.getScope());
        assertNull(saved.getClassId());
        assertEquals(subjectA.getSubjectId(), saved.getSubjectId());
        assertEquals(topicA.getTopicId(), saved.getTopicId());

        Optional<LearningMaterial> retrieved = learningMaterialRepository.findById(saved.getMaterialId());
        assertTrue(retrieved.isPresent());
        assertEquals(MaterialScope.SHARED, retrieved.get().getScope());
        assertNull(retrieved.get().getClassId());
    }

    @Test
    @DisplayName("2. SHARED Material có classId khác NULL bị từ chối")
    void test2_SharedMaterialWithClassIdRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'SHARED', ?, 'Invalid Shared Material', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectA.getSubjectId(), classA.getClassId()
            );
        }, "SHARED material with non-null class_id must violate chk_learning_materials_scope_class");
    }

    @Test
    @DisplayName("3. Tạo CLASS Material hợp lệ (scope=CLASS, class_id!=NULL)")
    void test3_ValidClassMaterialCreation() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class Material Valid 1")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .fileUrl("https://example.com/class1.pdf")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        assertNotNull(saved.getMaterialId(), "Material ID must be generated");
        createdMaterialIds.add(saved.getMaterialId());

        assertEquals(MaterialScope.CLASS, saved.getScope());
        assertEquals(classA.getClassId(), saved.getClassId());
        assertEquals(subjectA.getSubjectId(), saved.getSubjectId());
        assertEquals(topicA.getTopicId(), saved.getTopicId());

        Optional<LearningMaterial> retrieved = learningMaterialRepository.findById(saved.getMaterialId());
        assertTrue(retrieved.isPresent());
        assertEquals(MaterialScope.CLASS, retrieved.get().getScope());
        assertEquals(classA.getClassId(), retrieved.get().getClassId());
    }

    @Test
    @DisplayName("4. CLASS Material có classId NULL bị từ chối")
    void test4_ClassMaterialWithClassIdNullRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'CLASS', NULL, 'Invalid Class Material', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectA.getSubjectId()
            );
        }, "CLASS material with NULL class_id must violate chk_learning_materials_scope_class");
    }

    @Test
    @DisplayName("5. Scope không hợp lệ bị từ chối")
    void test5_InvalidScopeRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'INVALID_SCOPE', NULL, 'Invalid Scope Material', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectA.getSubjectId()
            );
        }, "Invalid scope must violate chk_learning_materials_scope");
    }

    @Test
    @DisplayName("6. Tạo CLASS Material có Class cùng Subject với Topic thành công")
    void test6_ClassMaterialSameSubjectClassAndTopicSucceeds() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Same Subject Class Material")
                .type(MaterialType.VIDEO)
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        assertNotNull(saved.getMaterialId());
        createdMaterialIds.add(saved.getMaterialId());

        assertEquals(subjectA.getSubjectId(), saved.getSubjectId());
        assertEquals(topicA.getTopicId(), saved.getTopicId());
        assertEquals(classA.getClassId(), saved.getClassId());
    }

    @Test
    @DisplayName("7. Tạo CLASS Material có Class khác Subject với Topic bị từ chối bằng PostgreSQL Composite FK")
    void test7_ClassMaterialMismatchedClassSubjectRejected() {
        // topicA thuộc subjectA, classB thuộc subjectB (khác subject!)
        // Trường hợp 1: gán subject_id = subjectA (vi phạm fk_materials_class_subject)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'CLASS', ?, 'Cross Subject Material A', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectA.getSubjectId(), classB.getClassId()
            );
        }, "Composite FK fk_materials_class_subject must reject Class from subjectB when material has subjectA");

        // Trường hợp 2: gán subject_id = subjectB (vi phạm fk_materials_topic_subject)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'CLASS', ?, 'Cross Subject Material B', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectB.getSubjectId(), classB.getClassId()
            );
        }, "Composite FK fk_materials_topic_subject must reject Topic from subjectA when material has subjectB");
    }

    @Test
    @DisplayName("8. Tạo Material có subjectId khác Subject của Topic bị từ chối")
    void test8_MaterialMismatchedTopicSubjectRejected() {
        // topicA thuộc subjectA, nhưng cố tình gán subject_id = subjectB
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO learning_materials (material_id, topic_id, subject_id, scope, class_id, title, type, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'SHARED', NULL, 'Mismatched Topic Subject', 'PDF', 'APPROVED', NOW(), NOW())",
                    UUID.randomUUID(), topicA.getTopicId(), subjectB.getSubjectId()
            );
        }, "Composite FK fk_materials_topic_subject must reject mismatched subject_id");
    }

    @Test
    @DisplayName("9. Xóa Class đang được CLASS Material tham chiếu bị chặn theo chính sách RESTRICT")
    void test9_DeleteClassReferencedByClassMaterialBlockedByRestrict() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class Material Blocking Delete")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        createdMaterialIds.add(saved.getMaterialId());

        // Cố xóa classA khi đang có CLASS material tham chiếu -> phải bị RESTRICT chặn lại
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("DELETE FROM classes WHERE class_id = ?", classA.getClassId());
        }, "Deleting class with referencing CLASS material must fail due to ON DELETE RESTRICT");

        // Xóa material trước
        jdbcTemplate.update("DELETE FROM learning_materials WHERE material_id = ?", saved.getMaterialId());
        createdMaterialIds.remove(saved.getMaterialId());

        // Sau khi đã xóa material, xóa classA phải thành công
        int rowsDeleted = jdbcTemplate.update("DELETE FROM classes WHERE class_id = ?", classA.getClassId());
        assertEquals(1, rowsDeleted);
        createdClassIds.remove(classA.getClassId());
    }

    @Test
    @DisplayName("10. Xóa Topic đang được Material tham chiếu bị chặn theo chính sách RESTRICT")
    void test10_DeleteTopicReferencedByMaterialBlockedByRestrict() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.SHARED)
                .classId(null)
                .title("Shared Material Blocking Topic Delete")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        createdMaterialIds.add(saved.getMaterialId());

        // Cố xóa topicA khi đang có material tham chiếu -> phải bị RESTRICT chặn lại
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("DELETE FROM topics WHERE topic_id = ?", topicA.getTopicId());
        }, "Deleting topic with referencing material must fail due to ON DELETE RESTRICT");

        // Xóa material trước
        jdbcTemplate.update("DELETE FROM learning_materials WHERE material_id = ?", saved.getMaterialId());
        createdMaterialIds.remove(saved.getMaterialId());

        // Sau khi đã xóa material, xóa topicA phải thành công
        int rowsDeleted = jdbcTemplate.update("DELETE FROM topics WHERE topic_id = ?", topicA.getTopicId());
        assertEquals(1, rowsDeleted);
        createdTopicIds.remove(topicA.getTopicId());
    }

    @Test
    @DisplayName("11. Hibernate Entity Mapping chính xác")
    void test11_HibernateEntityMapping() {
        LearningMaterial mat = LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Entity Mapping Verification")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(2)
                .fileUrl("https://example.com/mapping_test.pdf")
                .contentText("Nội dung text chi tiết")
                .sourceCitation("Giáo trình Vật lý đại cương 1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LearningMaterial saved = learningMaterialRepository.saveAndFlush(mat);
        createdMaterialIds.add(saved.getMaterialId());

        Optional<LearningMaterial> retrieved = learningMaterialRepository.findById(saved.getMaterialId());
        assertTrue(retrieved.isPresent());
        LearningMaterial m = retrieved.get();
        assertEquals(saved.getMaterialId(), m.getMaterialId());
        assertEquals(subjectA.getSubjectId(), m.getSubjectId());
        assertEquals(topicA.getTopicId(), m.getTopicId());
        assertEquals(MaterialScope.CLASS, m.getScope());
        assertEquals(classA.getClassId(), m.getClassId());
        assertEquals("Entity Mapping Verification", m.getTitle());
        assertEquals(MaterialType.PDF, m.getType());
        assertEquals(ApprovalStatus.APPROVED, m.getApprovalStatus());
        assertEquals(2, m.getVersion());
        assertEquals("https://example.com/mapping_test.pdf", m.getFileUrl());
        assertEquals("Nội dung text chi tiết", m.getContentText());
        assertEquals("Giáo trình Vật lý đại cương 1", m.getSourceCitation());

        // Kiểm tra enum mapping được lưu dưới dạng varchar đúng chuỗi trong DB
        String scopeInDb = jdbcTemplate.queryForObject(
                "SELECT scope FROM learning_materials WHERE material_id = ?",
                String.class,
                saved.getMaterialId()
        );
        assertEquals("CLASS", scopeInDb);

        String typeInDb = jdbcTemplate.queryForObject(
                "SELECT type FROM learning_materials WHERE material_id = ?",
                String.class,
                saved.getMaterialId()
        );
        assertEquals("PDF", typeInDb);

        String approvalInDb = jdbcTemplate.queryForObject(
                "SELECT approval_status FROM learning_materials WHERE material_id = ?",
                String.class,
                saved.getMaterialId()
        );
        assertEquals("APPROVED", approvalInDb);
    }

    @Test
    @DisplayName("12. Repository Query trả đúng SHARED và CLASS Material")
    void test12_RepositoryQuerySharedAndClassMaterials() {
        // Mat 1: SHARED, subjectA, topicA, APPROVED
        LearningMaterial mat1 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.SHARED)
                .classId(null)
                .title("Mat 1 Shared Approved")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .createdAt(Instant.now().minusSeconds(100))
                .updatedAt(Instant.now())
                .build());
        createdMaterialIds.add(mat1.getMaterialId());

        // Mat 2: SHARED, subjectA, topicA, PENDING
        LearningMaterial mat2 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.SHARED)
                .classId(null)
                .title("Mat 2 Shared Pending")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .createdAt(Instant.now().minusSeconds(80))
                .updatedAt(Instant.now())
                .build());
        createdMaterialIds.add(mat2.getMaterialId());

        // Mat 3: CLASS, subjectA, topicA, classA, APPROVED
        LearningMaterial mat3 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Mat 3 ClassA Approved")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now())
                .build());
        createdMaterialIds.add(mat3.getMaterialId());

        // Mat 4: CLASS, subjectA, topicA, classA, PENDING
        LearningMaterial mat4 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(subjectA.getSubjectId())
                .topicId(topicA.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Mat 4 ClassA Pending")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .createdAt(Instant.now().minusSeconds(40))
                .updatedAt(Instant.now())
                .build());
        createdMaterialIds.add(mat4.getMaterialId());

        // Mat 5: CLASS, subjectB, topicB, classB, APPROVED
        LearningMaterial mat5 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(subjectB.getSubjectId())
                .topicId(topicB.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classB.getClassId())
                .title("Mat 5 ClassB Approved")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .createdAt(Instant.now().minusSeconds(20))
                .updatedAt(Instant.now())
                .build());
        createdMaterialIds.add(mat5.getMaterialId());

        learningMaterialRepository.flush();

        // 1. findBySubjectIdAndScopeOrderByCreatedAtDesc (SHARED)
        List<LearningMaterial> sharedSubjectA = learningMaterialRepository.findBySubjectIdAndScopeOrderByCreatedAtDesc(
                subjectA.getSubjectId(), MaterialScope.SHARED
        );
        assertTrue(sharedSubjectA.stream().anyMatch(m -> m.getMaterialId().equals(mat1.getMaterialId())));
        assertTrue(sharedSubjectA.stream().anyMatch(m -> m.getMaterialId().equals(mat2.getMaterialId())));
        assertFalse(sharedSubjectA.stream().anyMatch(m -> m.getMaterialId().equals(mat3.getMaterialId())));
        assertFalse(sharedSubjectA.stream().anyMatch(m -> m.getMaterialId().equals(mat5.getMaterialId())));

        // 2. findByTopicIdAndScopeOrderByCreatedAtDesc (SHARED)
        List<LearningMaterial> sharedTopicA = learningMaterialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(
                topicA.getTopicId(), MaterialScope.SHARED
        );
        assertTrue(sharedTopicA.stream().anyMatch(m -> m.getMaterialId().equals(mat1.getMaterialId())));
        assertTrue(sharedTopicA.stream().anyMatch(m -> m.getMaterialId().equals(mat2.getMaterialId())));
        assertFalse(sharedTopicA.stream().anyMatch(m -> m.getMaterialId().equals(mat3.getMaterialId())));

        // 3. findByClassIdAndScopeOrderByCreatedAtDesc (CLASS)
        List<LearningMaterial> classAMaterials = learningMaterialRepository.findByClassIdAndScopeOrderByCreatedAtDesc(
                classA.getClassId(), MaterialScope.CLASS
        );
        assertEquals(2, classAMaterials.size());
        assertTrue(classAMaterials.stream().anyMatch(m -> m.getMaterialId().equals(mat3.getMaterialId())));
        assertTrue(classAMaterials.stream().anyMatch(m -> m.getMaterialId().equals(mat4.getMaterialId())));
        assertFalse(classAMaterials.stream().anyMatch(m -> m.getMaterialId().equals(mat5.getMaterialId())));

        // 4. findByClassIdAndTopicIdAndScopeOrderByCreatedAtDesc
        List<LearningMaterial> classATopicAMaterials = learningMaterialRepository.findByClassIdAndTopicIdAndScopeOrderByCreatedAtDesc(
                classA.getClassId(), topicA.getTopicId(), MaterialScope.CLASS
        );
        assertEquals(2, classATopicAMaterials.size());

        // 5. findSharedAndClassMaterials (Subject A + Class A)
        List<LearningMaterial> sharedAndClass = learningMaterialRepository.findSharedAndClassMaterials(
                subjectA.getSubjectId(), classA.getClassId()
        );
        assertTrue(sharedAndClass.stream().anyMatch(m -> m.getMaterialId().equals(mat1.getMaterialId())));
        assertTrue(sharedAndClass.stream().anyMatch(m -> m.getMaterialId().equals(mat2.getMaterialId())));
        assertTrue(sharedAndClass.stream().anyMatch(m -> m.getMaterialId().equals(mat3.getMaterialId())));
        assertTrue(sharedAndClass.stream().anyMatch(m -> m.getMaterialId().equals(mat4.getMaterialId())));
        assertFalse(sharedAndClass.stream().anyMatch(m -> m.getMaterialId().equals(mat5.getMaterialId())));

        // 6. findBySubjectIdAndScopeAndApprovalStatusOrderByCreatedAtDesc (SHARED + APPROVED)
        List<LearningMaterial> sharedApproved = learningMaterialRepository.findBySubjectIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(
                subjectA.getSubjectId(), MaterialScope.SHARED, ApprovalStatus.APPROVED
        );
        assertTrue(sharedApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat1.getMaterialId())));
        assertFalse(sharedApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat2.getMaterialId())));

        // 7. findByClassIdAndScopeAndApprovalStatusOrderByCreatedAtDesc (CLASS + APPROVED)
        List<LearningMaterial> classApproved = learningMaterialRepository.findByClassIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(
                classA.getClassId(), MaterialScope.CLASS, ApprovalStatus.APPROVED
        );
        assertEquals(1, classApproved.size());
        assertEquals(mat3.getMaterialId(), classApproved.get(0).getMaterialId());

        // 8. findSharedAndClassMaterialsByStatus (Subject A + Class A + APPROVED)
        List<LearningMaterial> sharedAndClassApproved = learningMaterialRepository.findSharedAndClassMaterialsByStatus(
                subjectA.getSubjectId(), classA.getClassId(), ApprovalStatus.APPROVED
        );
        assertTrue(sharedAndClassApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat1.getMaterialId())));
        assertTrue(sharedAndClassApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat3.getMaterialId())));
        assertFalse(sharedAndClassApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat2.getMaterialId())));
        assertFalse(sharedAndClassApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat4.getMaterialId())));
        assertFalse(sharedAndClassApproved.stream().anyMatch(m -> m.getMaterialId().equals(mat5.getMaterialId())));
    }

    @Test
    @DisplayName("13. Các record legacy giữ nguyên approvalStatus và metadata")
    void test13_LegacyRecordsRetainApprovalStatusAndMetadata() {
        // Kiểm tra không có bản ghi nào bị thiếu subject_id hoặc scope
        Integer nullSubjectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_materials WHERE subject_id IS NULL",
                Integer.class
        );
        assertEquals(0, nullSubjectCount, "All materials must have non-null subject_id");

        Integer nullScopeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_materials WHERE scope IS NULL",
                Integer.class
        );
        assertEquals(0, nullScopeCount, "All materials must have non-null scope");

        // Kiểm tra tất cả bản ghi legacy có class_id NULL đều mang scope 'SHARED'
        Integer invalidSharedLegacy = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_materials WHERE class_id IS NULL AND scope != 'SHARED'",
                Integer.class
        );
        assertEquals(0, invalidSharedLegacy, "All legacy materials with class_id NULL must be SHARED");

        // Kiểm tra tính toàn vẹn subject_id của material khớp với topics.subject_id
        Integer mismatchedSubjectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_materials lm JOIN topics t ON lm.topic_id = t.topic_id WHERE lm.subject_id != t.subject_id",
                Integer.class
        );
        assertEquals(0, mismatchedSubjectCount, "Every material subject_id must match its topic subject_id");

        // Kiểm tra approval_status còn nguyên vẹn và thuộc tập hợp hợp lệ
        Integer invalidStatusCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_materials WHERE approval_status IS NULL OR approval_status NOT IN ('PENDING', 'APPROVED', 'REJECTED')",
                Integer.class
        );
        assertEquals(0, invalidStatusCount, "All materials must have valid approval_status");
    }

    @Test
    @DisplayName("14. Kiểm tra migration không nhân bản Material")
    void test14_MigrationDoesNotDuplicateMaterials() {
        Integer duplicateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) - COUNT(DISTINCT material_id) FROM learning_materials",
                Integer.class
        );
        assertEquals(0, duplicateCount, "Material IDs must remain strictly unique with 0 duplicates");

        List<String> duplicateIds = jdbcTemplate.query(
                "SELECT material_id::text FROM learning_materials GROUP BY material_id HAVING COUNT(*) > 1",
                (rs, rowNum) -> rs.getString(1)
        );
        assertTrue(duplicateIds.isEmpty(), "No duplicated material_id should exist in learning_materials");
    }

    @Test
    @DisplayName("15. Kiểm tra migration không tác động QuestionBank, Chapter, ExamMatrix hoặc LearningProgress")
    void test15_MigrationDoesNotAffectOtherTables() {
        // 1. question_bank
        Integer qbTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'question_bank'",
                Integer.class
        );
        assertEquals(1, qbTableExists, "Table question_bank must exist intact");
        Integer qbCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_bank", Integer.class);
        assertNotNull(qbCount);

        // 2. chapters
        Integer chaptersTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'chapters'",
                Integer.class
        );
        assertEquals(1, chaptersTableExists, "Table chapters must exist intact");
        Integer chaptersCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chapters", Integer.class);
        assertNotNull(chaptersCount);

        // 3. exam_matrix
        Integer examMatrixTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exam_matrix'",
                Integer.class
        );
        assertEquals(1, examMatrixTableExists, "Table exam_matrix must exist intact");
        Integer examMatrixCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_matrix", Integer.class);
        assertNotNull(examMatrixCount);

        // 4. learning_progress
        Integer progressTableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'learning_progress'",
                Integer.class
        );
        assertEquals(1, progressTableExists, "Table learning_progress must exist intact");
        Integer progressCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_progress", Integer.class);
        assertNotNull(progressCount);
    }
}
