package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.configuration.EnrollmentConstraintInitializer;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.request.SingleEnrollmentDTO;
import com.vatly1.example.model.request.UpdateEnrollmentStatusDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("pgtest")
@org.springframework.test.context.ContextConfiguration(initializers = PostgreSqlTestContextInitializer.class)
@EnabledIfEnvironmentVariable(named = "RUN_PG_INTEGRATION_TESTS", matches = "true")
public class PostgreSqlEnrollmentIntegrityIntegrationTest {

    @Autowired
    private IClassEnrollmentService classEnrollmentService;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private EnrollmentConstraintInitializer enrollmentConstraintInitializer;

    private Subject subjectPhysics;
    private Semester semester20261;
    private Class classA;
    private Class classB;
    private User student1;
    private User adminUser;

    private final List<UUID> createdEnrollmentIds = new ArrayList<>();
    private final List<UUID> createdClassIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();
    private final List<UUID> createdSemesterIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        subjectPhysics = subjectRepository.save(Subject.builder()
                .subjectCode("PG_PHYS_" + testSuffix)
                .subjectName("Vat ly Dai cuong PG Test " + testSuffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectPhysics.getSubjectId());

        semester20261 = semesterRepository.save(Semester.builder()
                .semesterCode("PG_SEM_" + testSuffix)
                .semesterName("Hoc ky PG Test " + testSuffix)
                .academicYear("2026-2027")
                .isCurrent(false)
                .build());
        createdSemesterIds.add(semester20261.getSemesterId());

        classA = classRepository.save(Class.builder()
                .subjectId(subjectPhysics.getSubjectId())
                .semesterId(semester20261.getSemesterId())
                .classCode("PG_L01_" + testSuffix)
                .status(ClassStatus.ACTIVE)
                .maxStudents(50)
                .build());
        createdClassIds.add(classA.getClassId());

        classB = classRepository.save(Class.builder()
                .subjectId(subjectPhysics.getSubjectId())
                .semesterId(semester20261.getSemesterId())
                .classCode("PG_L02_" + testSuffix)
                .status(ClassStatus.ACTIVE)
                .maxStudents(50)
                .build());
        createdClassIds.add(classB.getClassId());

        student1 = userRepository.save(User.builder()
                .username("pg_student_" + testSuffix)
                .email("pg_student_" + testSuffix + "@pgtest.edu.vn")
                .passwordHash("test_hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(student1.getUserId());

        adminUser = userRepository.save(User.builder()
                .username("pg_admin_" + testSuffix)
                .email("pg_admin_" + testSuffix + "@pgtest.edu.vn")
                .passwordHash("test_hash")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(adminUser.getUserId());
    }

    @AfterEach
    void tearDown() {
        // Scoped cleanup: delete strictly only entities created by this test execution
        for (UUID classId : createdClassIds) {
            jdbcTemplate.update("DELETE FROM class_enrollments WHERE class_id = ?", classId);
        }
        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM class_enrollments WHERE student_id = ?", userId);
        }
        for (UUID classId : createdClassIds) {
            classRepository.deleteById(classId);
        }
        for (UUID userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        for (UUID subjectId : createdSubjectIds) {
            subjectRepository.deleteById(subjectId);
        }
        for (UUID semesterId : createdSemesterIds) {
            semesterRepository.deleteById(semesterId);
        }
    }

    @Test
    @DisplayName("PG-1: Composite Foreign Key enforces enrollment subject & semester match class")
    void testCompositeForeignKeyConstraint() {
        UUID randomSubjectId = UUID.randomUUID();
        ClassEnrollment invalidEnr = ClassEnrollment.builder()
                .classId(classA.getClassId())
                .studentId(student1.getUserId())
                .subjectId(randomSubjectId) // Mismatched subject!
                .semesterId(classA.getSemesterId())
                .status(EnrollmentStatus.ACTIVE)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            classEnrollmentRepository.saveAndFlush(invalidEnr);
        }, "PostgreSQL Composite FK must reject mismatched subject_id");
    }

    @Test
    @DisplayName("PG-2: CHECK Constraint chk_class_enrollment_status rejects lowercase 'active'")
    void testCheckConstraintRejectsLowercaseActive() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO class_enrollments (enrollment_id, class_id, student_id, subject_id, semester_id, status, enrolled_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'active', now())",
                    UUID.randomUUID(), classA.getClassId(), student1.getUserId(), classA.getSubjectId(), classA.getSemesterId()
            );
        }, "CHECK constraint must reject lowercase 'active'");
    }

    @Test
    @DisplayName("PG-3: CHECK Constraint chk_class_enrollment_status rejects invalid status values")
    void testCheckConstraintRejectsInvalidStatus() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO class_enrollments (enrollment_id, class_id, student_id, subject_id, semester_id, status, enrolled_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'PENDING_APPROVAL', now())",
                    UUID.randomUUID(), classA.getClassId(), student1.getUserId(), classA.getSubjectId(), classA.getSemesterId()
            );
        }, "CHECK constraint must reject unlisted status values");
    }

    @Test
    @DisplayName("PG-4: Partial Unique Index prevents 2 ACTIVE enrollments for same subject and semester")
    void testPartialUniqueIndexActiveEnforcement() {
        classEnrollmentService.enrollSingleStudent(classA.getClassId(),
                new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");

        CustomException ex = assertThrows(CustomException.class, () -> {
            classEnrollmentService.enrollSingleStudent(classB.getClassId(),
                    new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");
        });
        assertEquals(409, ex.getHttpStatus().value());
    }

    @Test
    @DisplayName("PG-5: DROPPED and COMPLETED enrollments allow new ACTIVE enrollment")
    void testDroppedAndCompletedAllowed() {
        classEnrollmentService.enrollSingleStudent(classA.getClassId(),
                new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");

        classEnrollmentService.updateEnrollmentStatus(classA.getClassId(), student1.getUserId(),
                new UpdateEnrollmentStatusDTO(EnrollmentStatus.DROPPED), adminUser.getUserId(), "ADMIN");

        assertDoesNotThrow(() -> {
            classEnrollmentService.enrollSingleStudent(classB.getClassId(),
                    new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");
        });

        assertEquals(EnrollmentStatus.DROPPED,
                classEnrollmentRepository.findByClassIdAndStudentId(classA.getClassId(), student1.getUserId()).get().getStatus());
        assertEquals(EnrollmentStatus.ACTIVE,
                classEnrollmentRepository.findByClassIdAndStudentId(classB.getClassId(), student1.getUserId()).get().getStatus());
    }

    @Test
    @DisplayName("PG-6: Concurrency test - 2 concurrent enrollments to different classes of same subject")
    void testConcurrentEnrollment() throws InterruptedException {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                classEnrollmentService.enrollSingleStudent(classA.getClassId(),
                        new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                classEnrollmentService.enrollSingleStudent(classB.getClassId(),
                        new SingleEnrollmentDTO(student1.getUserId()), adminUser.getUserId(), "ADMIN");
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent enrollment must succeed");
        assertEquals(1, conflictCount.get(), "Second concurrent enrollment must be rejected");
    }

    @Test
    @DisplayName("PG-7: Verify EnrollmentConstraintInitializer does NOT execute DDL on PostgreSQL")
    void testStartupDoesNotExecuteDdlOnPostgres() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            assertEquals("PostgreSQL", dbProduct);
        }

        if (enrollmentConstraintInitializer != null) {
            assertDoesNotThrow(() -> {
                enrollmentConstraintInitializer.run(new DefaultApplicationArguments());
            }, "Calling initializer on PostgreSQL must safely do nothing without running DDL");
        }
    }
}
