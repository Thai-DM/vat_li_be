package com.vatly1.example.concurrency;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.dto.ExamAttemptDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.AttemptStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
class ExamConcurrencyIntegrationTest {

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private IClassRepository classRepository;

    private User teacher;
    private User student;
    private Exam officialExam;

    @BeforeEach
    void setUp() {
        teacher = userRepository.findByUsername("gv_nguyen");
        assertNotNull(teacher);

        student = userRepository.findByUsername("sv_an");
        assertNotNull(student);

        Subject subject = subjectRepository.findAll().stream().findFirst().orElseGet(() -> {
            Subject s = Subject.builder().subjectCode("PHY101_C").subjectName("Vật lý 1 Concurrency").description("Concurrency test").isActive(true).build();
            return subjectRepository.save(s);
        });

        Semester semester = semesterRepository.findAll().stream().findFirst().orElseGet(() -> {
            Semester sem = Semester.builder().semesterCode("2026_C").semesterName("HK1 2026 Concurrency")
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(4)).isCurrent(false).build();
            return semesterRepository.save(sem);
        });

        Class clazz = classRepository.findAll().stream().findFirst().orElseGet(() -> {
            Class c = Class.builder().classCode("PHY_CONC_01")
                    .subjectId(subject.getSubjectId()).semesterId(semester.getSemesterId()).instructorId(teacher.getUserId()).status(ClassStatus.ACTIVE).maxStudents(100).build();
            return classRepository.save(c);
        });

        officialExam = examRepository.save(Exam.builder()
                .classId(clazz.getClassId())
                .title("Kỳ thi Giữa kỳ Concurrency Check")
                .examType(ExamType.MIDTERM)
                .durationMinutes(60)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                .createdBy(teacher.getUserId())
                .build());
    }

    @Test
    @DisplayName("CONC-01: 10 luồng đồng thời gọi startAttempt cho kỳ thi chính thức -> Chỉ duy nhất 1 luồng thành công, 9 luồng bị 409 Conflict")
    void testCONC01_ConcurrentStartAttempt_OfficialExam_OnlyOneWins() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Release all threads at the exact same instant
                    examService.startAttempt(officialExam.getExamId(), student.getUserId());
                    successCount.incrementAndGet();
                } catch (CustomException ce) {
                    if (ce.getHttpStatus() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(ce);
                    }
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // BANG! All threads start concurrently

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, unexpectedExceptions.size(), "No unexpected exceptions should occur");
        assertEquals(1, successCount.get(), "Exactly 1 concurrent attempt must succeed");
        assertEquals(threadCount - 1, conflictCount.get(), "Remaining attempts must receive 409 Conflict");

        // Verify database state: exactly 1 record in exam_attempts
        long dbAttempts = examAttemptRepository.countByExamIdAndStudentId(officialExam.getExamId(), student.getUserId());
        assertEquals(1, dbAttempts, "Database must store exactly 1 attempt without duplicates");
    }

    @Test
    @DisplayName("CONC-02: 10 luồng đồng thời gọi submitAttempt cho cùng 1 attempt -> Chỉ duy nhất 1 luồng chấm điểm, không bị race condition")
    void testCONC02_ConcurrentSubmitAttempt_SameAttempt_OnlyOneSucceeds() throws Exception {
        // Start an attempt first
        User testStudent = userRepository.findByUsername("sv_cuong");
        ExamAttemptDTO attempt = examService.startAttempt(officialExam.getExamId(), testStudent.getUserId());
        assertNotNull(attempt);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadySubmittedCount = new AtomicInteger(0);
        List<Exception> otherExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous execution
                    examService.submitAttempt(attempt.getAttemptId(), testStudent.getUserId());
                    successCount.incrementAndGet();
                } catch (CustomException ce) {
                    if (ce.getHttpStatus() == HttpStatus.BAD_REQUEST && ce.getMessage().contains("already submitted")) {
                        alreadySubmittedCount.incrementAndGet();
                    } else {
                        otherExceptions.add(ce);
                    }
                } catch (Exception e) {
                    otherExceptions.add(e);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Trigger simultaneous submission

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, otherExceptions.size(), "No unexpected exceptions");
        assertEquals(1, successCount.get(), "Only 1 thread successfully marks and grades the attempt");
        assertEquals(threadCount - 1, alreadySubmittedCount.get(), "Other 9 threads rejected with 'already submitted'");

        // Verify final attempt status in database
        var finalAttempt = examAttemptRepository.findById(attempt.getAttemptId()).orElseThrow();
        assertEquals(AttemptStatus.GRADED, finalAttempt.getStatus());
        assertNotNull(finalAttempt.getSubmittedAt());
    }

    @Test
    @DisplayName("CONC-03: 15 sinh viên khác nhau đồng thời nộp bài thi -> Tất cả 15 lượt đều thành công, không bị deadlock")
    void testCONC03_MultiStudentConcurrentSubmission_NoDeadlock() throws Exception {
        int studentCount = 15;
        List<User> students = new ArrayList<>();
        List<ExamAttemptDTO> attempts = new ArrayList<>();

        for (int i = 0; i < studentCount; i++) {
            String username = "conc_sv_" + i;
            User u = userRepository.findByUsername(username);
            if (u == null) {
                u = userRepository.save(User.builder()
                        .username(username)
                        .passwordHash("password123")
                        .email(username + "@test.vn")
                        .role(UserRole.STUDENT)
                        .status(UserStatus.ACTIVE)
                        .build());
            }
            students.add(u);
            ExamAttemptDTO a = examService.startAttempt(officialExam.getExamId(), u.getUserId());
            attempts.add(a);
        }

        ExecutorService executor = Executors.newFixedThreadPool(studentCount);
        CountDownLatch readyLatch = new CountDownLatch(studentCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < studentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    examService.submitAttempt(attempts.get(index).getAttemptId(), students.get(index).getUserId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(0, errors.size(), "No deadlock or database collision occurred");
        assertEquals(studentCount, successCount.get(), "All 15 students successfully submitted their exam");
    }
}
