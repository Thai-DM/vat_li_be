package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
class BulkFinalExamConcurrencyTest {

    @Autowired
    private IExamMatrixService examMatrixService;

    @Autowired
    private IExamService examService;

    @Autowired
    private IExamMatrixRepository examMatrixRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User admin;
    private Subject subject;
    private Topic topic;
    private Class classA;
    private Class classB;
    private ExamMatrixDTO publishedMatrix;

    @BeforeEach
    void setUp() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            admin = userRepository.save(User.builder()
                    .username("conc_admin_" + UUID.randomUUID())
                    .email("conc_admin_" + UUID.randomUUID() + "@test.com")
                    .role(UserRole.ADMIN)
                    .build());

            subject = subjectRepository.save(Subject.builder()
                    .subjectCode("CONC101_" + UUID.randomUUID().toString().substring(0, 6))
                    .subjectName("Concurrency Test Subject")
                    .build());

            topic = topicRepository.save(Topic.builder()
                    .subjectId(subject.getSubjectId())
                    .topicName("Concurrency Topic")
                    .orderIndex(1)
                    .build());

            classA = classRepository.save(Class.builder()
                    .subjectId(subject.getSubjectId())
                    .classCode("CONC_CLA_" + UUID.randomUUID().toString().substring(0, 6))
                    .status(ClassStatus.ACTIVE)
                    .build());

            classB = classRepository.save(Class.builder()
                    .subjectId(subject.getSubjectId())
                    .classCode("CONC_CLB_" + UUID.randomUUID().toString().substring(0, 6))
                    .status(ClassStatus.ACTIVE)
                    .build());

            // 2 questions
            for (int i = 1; i <= 2; i++) {
                QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                        .subjectId(subject.getSubjectId())
                        .topicId(topic.getTopicId())
                        .content("Concurrency Question " + i + "?")
                        .questionType(QuestionType.MCQ_SINGLE)
                        .difficultyLevel(DifficultyLevel.EASY)
                        .approvalStatus(ApprovalStatus.APPROVED)
                        .createdBy(admin.getUserId())
                        .createdAt(Instant.now())
                        .build());

                questionOptionRepository.save(QuestionOption.builder()
                        .questionId(q.getQuestionId())
                        .optionText("Option A")
                        .isCorrect(true)
                        .orderIndex(1)
                        .build());
            }

            // Create and publish matrix
            ExamMatrixDetailRequestDTO detailDto = new ExamMatrixDetailRequestDTO();
            detailDto.setTopicId(topic.getTopicId());
            detailDto.setDifficultyLevel(DifficultyLevel.EASY);
            detailDto.setNumQuestions(1);

            CreateExamMatrixDTO createMatrixDTO = new CreateExamMatrixDTO();
            createMatrixDTO.setSubjectId(subject.getSubjectId());
            createMatrixDTO.setMatrixName("Concurrency Matrix");
            createMatrixDTO.setExamType(ExamType.FINAL);
            createMatrixDTO.setDetails(List.of(detailDto));

            ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(createMatrixDTO, admin.getUserId());
            publishedMatrix = examMatrixService.publishMatrix(draft.getMatrixId(), admin.getUserId());
        });
    }

    @Test
    @DisplayName("Bulk Provision Concurrency: Reverse order batches avoid deadlock via UUID sorting")
    void testReverseOrderBatchesDoNotDeadlock() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<UUID> batch1 = List.of(classA.getClassId(), classB.getClassId());
        List<UUID> batch2 = List.of(classB.getClassId(), classA.getClassId());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            barrier.await();
            try {
                BulkProvisionFinalExamDTO dto = new BulkProvisionFinalExamDTO();
                dto.setMatrixId(publishedMatrix.getMatrixId());
                dto.setClassIds(batch1);
                dto.setDurationMinutes(60);
                dto.setStartTime(Instant.now().minus(5, ChronoUnit.MINUTES));
                dto.setEndTime(Instant.now().plus(55, ChronoUnit.MINUTES));
                dto.setAutoGenerateQuestions(true);

                examService.bulkProvisionFinalExams(dto, admin.getUserId());
                successCount.incrementAndGet();
            } catch (CustomException ex) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            barrier.await();
            try {
                BulkProvisionFinalExamDTO dto = new BulkProvisionFinalExamDTO();
                dto.setMatrixId(publishedMatrix.getMatrixId());
                dto.setClassIds(batch2);
                dto.setDurationMinutes(60);
                dto.setStartTime(Instant.now().minus(5, ChronoUnit.MINUTES));
                dto.setEndTime(Instant.now().plus(55, ChronoUnit.MINUTES));
                dto.setAutoGenerateQuestions(true);

                examService.bulkProvisionFinalExams(dto, admin.getUserId());
                successCount.incrementAndGet();
            } catch (CustomException ex) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        // Neither thread should deadlock; both should terminate within 15 seconds
        assertDoesNotThrow(() -> f1.get(15, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> f2.get(15, TimeUnit.SECONDS));

        executor.shutdown();

        // Exactly one batch should win and one should fail with conflict because of duplicate exam detection
        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());
    }

    @Test
    @DisplayName("Bulk Provision Atomicity: If one class fails, entire batch rolls back")
    void testBatchRollbackAtomicityOnSingleClassFailure() {
        // Create Class C with DIFFERENT subject
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Class classC = template.execute(status -> {
            Subject otherSubject = subjectRepository.save(Subject.builder()
                    .subjectCode("OTH101_" + UUID.randomUUID().toString().substring(0, 6))
                    .subjectName("Other Subject")
                    .build());
            return classRepository.save(Class.builder()
                    .subjectId(otherSubject.getSubjectId())
                    .classCode("OTH_CLC_" + UUID.randomUUID().toString().substring(0, 6))
                    .status(ClassStatus.ACTIVE)
                    .build());
        });

        // Batch containing valid classA and invalid classC (different subject)
        BulkProvisionFinalExamDTO dto = new BulkProvisionFinalExamDTO();
        dto.setMatrixId(publishedMatrix.getMatrixId());
        dto.setClassIds(List.of(classA.getClassId(), classC.getClassId()));
        dto.setDurationMinutes(60);
        dto.setStartTime(Instant.now().minus(5, ChronoUnit.MINUTES));
        dto.setEndTime(Instant.now().plus(55, ChronoUnit.MINUTES));

        assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, admin.getUserId()));

        // Verification: No exam should exist for classA either (full rollback!)
        boolean examAExists = examRepository.existsByClassIdAndMatrixId(classA.getClassId(), publishedMatrix.getMatrixId());
        assertFalse(examAExists, "Transaction must rollback entirely when one class in the batch fails");
    }
}
