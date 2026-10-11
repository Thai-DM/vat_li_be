package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamMatrixService;
import com.vatly1.example.utils.JwtTokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BulkFinalExamProvisioningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenUtils jwtTokenUtils;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IQuestionCloRepository questionCloRepository;

    @Autowired
    private IExamMatrixService examMatrixService;

    private User adminUser;
    private User instructorUser;
    private User studentUser;
    private String adminToken;
    private String instructorToken;
    private String studentToken;
    private Subject subject;
    private Class testClass;
    private Topic topic;
    private Clo clo;
    private ExamMatrixDTO publishedMatrix;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(User.builder()
                .username("adm_bpctrl_" + suffix)
                .email("adm_bpctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .build());

        instructorUser = userRepository.save(User.builder()
                .username("inst_bpctrl_" + suffix)
                .email("inst_bpctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.INSTRUCTOR)
                .build());

        studentUser = userRepository.save(User.builder()
                .username("stu_bpctrl_" + suffix)
                .email("stu_bpctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .build());

        adminToken = jwtTokenUtils.createToken(adminUser.getUsername(), adminUser.getRole(), adminUser.getUserId());
        instructorToken = jwtTokenUtils.createToken(instructorUser.getUsername(), instructorUser.getRole(), instructorUser.getUserId());
        studentToken = jwtTokenUtils.createToken(studentUser.getUsername(), studentUser.getRole(), studentUser.getUserId());

        subject = subjectRepository.save(Subject.builder()
                .subjectCode("BP_" + suffix)
                .subjectName("Vật lý BP " + suffix)
                .isActive(true)
                .build());

        testClass = classRepository.save(Class.builder()
                .subjectId(subject.getSubjectId())
                .semesterId(UUID.randomUUID())
                .classCode("BP-CLA-" + suffix)
                .instructorId(instructorUser.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        topic = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .topicName("Topic BP " + suffix)
                .orderIndex(1)
                .build());

        clo = cloRepository.save(Clo.builder()
                .subjectId(subject.getSubjectId())
                .cloCode("CLO_BP_" + suffix)
                .description("CLO BP")
                .orderIndex(1)
                .isActive(true)
                .build());

        // Seed 2 APPROVED questions
        for (int i = 0; i < 2; i++) {
            QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                    .subjectId(subject.getSubjectId())
                    .topicId(topic.getTopicId())
                    .content("Câu hỏi BP " + i + " " + UUID.randomUUID())
                    .questionType(QuestionType.MCQ_SINGLE)
                    .difficultyLevel(DifficultyLevel.EASY)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .createdBy(adminUser.getUserId())
                    .build());

            questionCloRepository.save(QuestionClo.builder()
                    .questionId(q.getQuestionId())
                    .cloId(clo.getCloId())
                    .subjectId(subject.getSubjectId())
                    .build());
        }

        CreateExamMatrixDTO createDto = CreateExamMatrixDTO.builder()
                .subjectId(subject.getSubjectId())
                .matrixName("Matrix BP " + suffix)
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .cloId(clo.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(createDto, adminUser.getUserId());
        publishedMatrix = examMatrixService.publishMatrix(draft.getMatrixId(), adminUser.getUserId());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exams/bulk-provision-final: ADMIN gọi thành công (201 CREATED)")
    void testAdminBulkProvisionFinal_201Created() throws Exception {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedMatrix.getMatrixId())
                .classIds(List.of(testClass.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .autoGenerateQuestions(true)
                .build();

        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.totalClassesRequested").value(1))
                .andExpect(jsonPath("$.data.totalClassesProvisioned").value(1))
                .andExpect(jsonPath("$.data.createdExams[0].examType").value("FINAL"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/exams/bulk-provision-final: INSTRUCTOR bị chặn (403 FORBIDDEN)")
    void testInstructorBulkProvisionFinal_403Forbidden() throws Exception {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedMatrix.getMatrixId())
                .classIds(List.of(testClass.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exams/bulk-provision-final: STUDENT bị chặn (403 FORBIDDEN)")
    void testStudentBulkProvisionFinal_403Forbidden() throws Exception {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedMatrix.getMatrixId())
                .classIds(List.of(testClass.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exams/bulk-provision-final: Lỗi validation thiếu thông tin (400 BAD REQUEST)")
    void testValidationError_NullMatrixId_400BadRequest() throws Exception {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .classIds(List.of(testClass.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exams/bulk-provision-final: Tạo trùng lặp trả về HTTP 409 CONFLICT")
    void testDuplicateProvision_409Conflict() throws Exception {
        BulkProvisionFinalExamDTO dto = BulkProvisionFinalExamDTO.builder()
                .matrixId(publishedMatrix.getMatrixId())
                .classIds(List.of(testClass.getClassId()))
                .durationMinutes(90)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build();

        // 1st call
        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // 2nd call -> Conflict
        mockMvc.perform(post("/api/v1/admin/exams/bulk-provision-final")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }
}
