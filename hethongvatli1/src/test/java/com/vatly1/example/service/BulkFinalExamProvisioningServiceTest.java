package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class BulkFinalExamProvisioningServiceTest {

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
    private IClassRepository classRepository;

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
    private Subject subjectPhys;
    private Subject subjectMath;
    private UUID semesterId1;
    private UUID semesterId2;
    private Class classA;
    private Class classB;
    private Class classWrongSubject;
    private Class classWrongSemester;
    private Topic topicPhys1;
    private Clo cloPhys1;
    private ExamMatrixDTO publishedFinalMatrix;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(User.builder()
                .username("admin_bp_" + suffix)
                .email("admin_bp_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .build());

        instructorUser = userRepository.save(User.builder()
                .username("inst_bp_" + suffix)
                .email("inst_bp_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.INSTRUCTOR)
                .build());

        subjectPhys = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_" + suffix)
                .subjectName("Vật lý 1 " + suffix)
                .isActive(true)
                .build());

        subjectMath = subjectRepository.save(Subject.builder()
                .subjectCode("MATH_" + suffix)
                .subjectName("Toán 1 " + suffix)
                .isActive(true)
                .build());

        semesterId1 = UUID.randomUUID();
        semesterId2 = UUID.randomUUID();

        classA = classRepository.save(Class.builder()
                .subjectId(subjectPhys.getSubjectId())
                .semesterId(semesterId1)
                .classCode("PHYS-CLA-" + suffix)
                .instructorId(instructorUser.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        classB = classRepository.save(Class.builder()
                .subjectId(subjectPhys.getSubjectId())
                .semesterId(semesterId1)
                .classCode("PHYS-CLB-" + suffix)
                .instructorId(instructorUser.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        classWrongSubject = classRepository.save(Class.builder()
                .subjectId(subjectMath.getSubjectId())
                .semesterId(semesterId1)
                .classCode("MATH-CLA-" + suffix)
                .instructorId(instructorUser.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        classWrongSemester = classRepository.save(Class.builder()
                .subjectId(subjectPhys.getSubjectId())
                .semesterId(semesterId2)
                .classCode("PHYS-SEM2-" + suffix)
                .instructorId(instructorUser.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        topicPhys1 = topicRepository.save(Topic.builder()
                .subjectId(subjectPhys.getSubjectId())
                .topicName("Cơ học " + suffix)
                .orderIndex(1)
                .build());

        cloPhys1 = cloRepository.save(Clo.builder()
                .subjectId(subjectPhys.getSubjectId())
                .cloCode("CLO_P1_" + suffix)
                .description("Vận dụng cơ học")
                .orderIndex(1)
                .isActive(true)
                .build());

        // Seed 4 APPROVED questions for physics
        for (int i = 0; i < 4; i++) {
            QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                    .subjectId(subjectPhys.getSubjectId())
                    .topicId(topicPhys1.getTopicId())
                    .content("Câu hỏi vật lý " + i + " " + UUID.randomUUID())
                    .questionType(QuestionType.MCQ_SINGLE)
                    .difficultyLevel(DifficultyLevel.EASY)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .createdBy(adminUser.getUserId())
                    .build());

            questionCloRepository.save(QuestionClo.builder()
                    .questionId(q.getQuestionId())
                    .cloId(cloPhys1.getCloId())
                    .subjectId(subjectPhys.getSubjectId())
                    .build());
        }

        // Create and publish a Common FINAL Matrix
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .matrixName("Ma trận Thi Chung " + suffix)
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicPhys1.getTopicId())
                                .cloId(cloPhys1.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(2)
                                .weightPercent(BigDecimal.ONE)
                                .build()
                ))
                .build();
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        publishedFinalMatrix = examMatrixService.publishMatrix(draft.getMatrixId(), adminUser.getUserId());
    }

    @Test
    @DisplayName("1. ADMIN bulk-provision FINAL exam cho 2 lớp thành công (mỗi lớp có exam riêng, câu hỏi riêng)")
    void testAdminBulkProvisionFinal_TwoClasses_Success() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId(), classB.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .semesterId(semesterId1)
                .autoGenerateQuestions(true)
                .build();

        BulkProvisionFinalExamResultDTO result = examService.bulkProvisionFinalExams(dto, adminUser.getUserId());

        assertNotNull(result);
        assertEquals(publishedFinalMatrix.getMatrixId(), result.getMatrixId());
        assertEquals(2, result.getTotalClassesRequested());
        assertEquals(2, result.getTotalClassesProvisioned());
        assertEquals(2, result.getCreatedExams().size());

        ExamDTO examA = result.getCreatedExams().get(0);
        ExamDTO examB = result.getCreatedExams().get(1);

        assertNotEquals(examA.getExamId(), examB.getExamId());
        assertEquals(classA.getClassId(), examA.getClassId());
        assertEquals(classB.getClassId(), examB.getClassId());
        assertEquals(ExamType.FINAL, examA.getExamType());
        assertEquals(ExamType.FINAL, examB.getExamType());
        assertEquals(publishedFinalMatrix.getMatrixId(), examA.getMatrixId());
        assertEquals(publishedFinalMatrix.getMatrixId(), examB.getMatrixId());

        // Verify questions were populated
        long qCountA = examQuestionRepository.countByExamId(examA.getExamId());
        long qCountB = examQuestionRepository.countByExamId(examB.getExamId());
        assertEquals(2, qCountA);
        assertEquals(2, qCountB);
    }

    @Test
    @DisplayName("2. Bulk-provision thất bại khi có lớp khác Subject (400 BAD REQUEST)")
    void testBulkProvision_ClassDifferentSubject_BadRequest() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId(), classWrongSubject.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("không thuộc môn học"));

        // Verify atomic rollback: classA must NOT have any exams created
        List<Exam> examsForClassA = examRepository.findByClassId(classA.getClassId());
        assertTrue(examsForClassA.isEmpty());
    }

    @Test
    @DisplayName("3. Bulk-provision thất bại khi lớp không thuộc Semester được chỉ định (400 BAD REQUEST)")
    void testBulkProvision_ClassDifferentSemester_BadRequest() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId(), classWrongSemester.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .semesterId(semesterId1)
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("không thuộc học kỳ"));

        // Verify atomic rollback
        assertTrue(examRepository.findByClassId(classA.getClassId()).isEmpty());
    }

    @Test
    @DisplayName("4. Không cho phép provision từ Matrix DRAFT hoặc ARCHIVED (400 BAD REQUEST)")
    void testBulkProvision_MatrixNotPublished_BadRequest() {
        // Create draft matrix
        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .matrixName("Draft Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicPhys1.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO draftMatrix = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());

        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(draftMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("PUBLISHED"));
    }

    @Test
    @DisplayName("5. Chặn request chứa UUID lớp học trùng lặp (400 BAD REQUEST)")
    void testBulkProvision_DuplicateClassIdsInRequest_BadRequest() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId(), classA.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, adminUser.getUserId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("trùng lặp"));
    }

    @Test
    @DisplayName("6. Request gửi lại cùng class và matrix revision bị từ chối chống trùng lặp (409 CONFLICT)")
    void testBulkProvision_DuplicateProvision_Conflict() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        // First provision
        examService.bulkProvisionFinalExams(dto, adminUser.getUserId());

        // Replay provision
        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, adminUser.getUserId()));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("đã có bài thi FINAL"));
    }

    @Test
    @DisplayName("7. INSTRUCTOR không thể gọi Bulk Provisioning (403 FORBIDDEN)")
    void testInstructorCannotBulkProvision_Forbidden() {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedFinalMatrix.getMatrixId())
                .classIds(List.of(classA.getClassId()))
                .durationMinutes(60)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                examService.bulkProvisionFinalExams(dto, instructorUser.getUserId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }
}
