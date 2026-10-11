package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CommonFinalMatrixControllerTest {

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
    private Subject testSubject;
    private Topic topic;
    private Clo clo;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(User.builder()
                .username("adm_ctrl_" + suffix)
                .email("adm_ctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .build());

        instructorUser = userRepository.save(User.builder()
                .username("inst_ctrl_" + suffix)
                .email("inst_ctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.INSTRUCTOR)
                .build());

        studentUser = userRepository.save(User.builder()
                .username("stu_ctrl_" + suffix)
                .email("stu_ctrl_" + suffix + "@hust.edu.vn")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .build());

        adminToken = jwtTokenUtils.createToken(adminUser.getUsername(), adminUser.getRole(), adminUser.getUserId());
        instructorToken = jwtTokenUtils.createToken(instructorUser.getUsername(), instructorUser.getRole(), instructorUser.getUserId());
        studentToken = jwtTokenUtils.createToken(studentUser.getUsername(), studentUser.getRole(), studentUser.getUserId());

        testSubject = subjectRepository.save(Subject.builder()
                .subjectCode("CTRL_" + suffix)
                .subjectName("Vật lý CTRL " + suffix)
                .isActive(true)
                .build());

        topic = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .topicName("Topic CTRL " + suffix)
                .orderIndex(1)
                .build());

        clo = cloRepository.save(Clo.builder()
                .subjectId(testSubject.getSubjectId())
                .cloCode("CLO_CTRL_" + suffix)
                .description("CLO desc")
                .orderIndex(1)
                .isActive(true)
                .build());

        // Seed 2 APPROVED questions
        for (int i = 0; i < 2; i++) {
            QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                    .subjectId(testSubject.getSubjectId())
                    .topicId(topic.getTopicId())
                    .content("Câu hỏi " + i + " " + UUID.randomUUID())
                    .questionType(QuestionType.MCQ_SINGLE)
                    .difficultyLevel(DifficultyLevel.EASY)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .createdBy(adminUser.getUserId())
                    .build());

            questionCloRepository.save(QuestionClo.builder()
                    .questionId(q.getQuestionId())
                    .cloId(clo.getCloId())
                    .subjectId(testSubject.getSubjectId())
                    .build());
        }
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/final: ADMIN tạo Common FINAL Matrix thành công (201 CREATED)")
    void testAdminCreateFinalMatrix_201Created() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Common Final Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .cloId(clo.getCloId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .weightPercent(BigDecimal.ONE)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/admin/exam-matrices/final")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.matrixName").value("Common Final Matrix"))
                .andExpect(jsonPath("$.data.examType").value("FINAL"))
                .andExpect(jsonPath("$.data.matrixStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.isLocked").value(false))
                .andExpect(jsonPath("$.data.revisionNumber").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/final: INSTRUCTOR bị chặn (403 FORBIDDEN)")
    void testInstructorCreateFinalMatrix_403Forbidden() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Instructor Final Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/admin/exam-matrices/final")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/final: STUDENT bị chặn (403 FORBIDDEN)")
    void testStudentCreateFinalMatrix_403Forbidden() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Student Final Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/admin/exam-matrices/final")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/{id}/publish: ADMIN publish matrix thành công (200 OK)")
    void testAdminPublishMatrix_200OK() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Publish")
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
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(dto, adminUser.getUserId());

        mockMvc.perform(post("/api/v1/admin/exam-matrices/" + draft.getMatrixId() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrixStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.isLocked").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/{id}/archive: ADMIN archive matrix thành công (200 OK)")
    void testAdminArchiveMatrix_200OK() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Archive")
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
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(dto, adminUser.getUserId());
        examMatrixService.publishMatrix(draft.getMatrixId(), adminUser.getUserId());

        mockMvc.perform(post("/api/v1/admin/exam-matrices/" + draft.getMatrixId() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrixStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.isLocked").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/admin/exam-matrices/{id}/clone-revision: ADMIN clone revision thành công (201 CREATED)")
    void testAdminCloneRevision_201Created() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Matrix to Clone")
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
        ExamMatrixDTO draft = examMatrixService.createFinalExamMatrix(dto, adminUser.getUserId());

        mockMvc.perform(post("/api/v1/admin/exam-matrices/" + draft.getMatrixId() + "/clone-revision")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revisionNumber").value(2))
                .andExpect(jsonPath("$.data.matrixStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.isLocked").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/exam-matrices: INSTRUCTOR tạo FINAL matrix qua legacy endpoint bị chặn (403 FORBIDDEN)")
    void testInstructorCreateFinalViaLegacyEndpoint_403Forbidden() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Instructor Sneaky Final Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/exam-matrices: INSTRUCTOR tạo PRACTICE matrix thành công (201 CREATED)")
    void testInstructorCreatePracticeViaLegacyEndpoint_201Created() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Instructor Practice Matrix")
                .examType(ExamType.PRACTICE)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.examType").value("PRACTICE"));
    }

    @Test
    @DisplayName("GET /api/v1/exam-matrices/{id}: STUDENT bị chặn xem chi tiết ma trận (403 FORBIDDEN)")
    void testStudentAccessMatrixDetails_403Forbidden() throws Exception {
        CreateExamMatrixDTO dto = CreateExamMatrixDTO.builder()
                .subjectId(testSubject.getSubjectId())
                .matrixName("Secret Matrix")
                .examType(ExamType.FINAL)
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topic.getTopicId())
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build()
                ))
                .build();
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(dto, adminUser.getUserId());

        mockMvc.perform(get("/api/v1/exam-matrices/" + created.getMatrixId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
