package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
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
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExamMatrixControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    private String instructorToken;
    private String studentToken;
    private UUID classId;
    private UUID subjectId;
    private UUID topicId;

    @BeforeEach
    void setUp() throws Exception {
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");

        User gvNguyen = userRepository.findByUsername("gv_nguyen");
        Class targetClass = classRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getInstructorId(), gvNguyen.getUserId()))
                .findFirst()
                .orElseThrow();
        classId = targetClass.getClassId();
        subjectId = targetClass.getSubjectId();

        List<Topic> topics = topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId);
        if (!topics.isEmpty()) {
            topicId = topics.get(0).getTopicId();
        } else {
            Topic newTopic = topicRepository.save(Topic.builder()
                    .subjectId(subjectId)
                    .topicName("Chương 1: Cơ học cổ điển")
                    .orderIndex(1)
                    .build());
            topicId = newTopic.getTopicId();
        }

        // Ensure questions exist in question bank for topicId
        QuestionBank q1 = QuestionBank.builder()
                .subjectId(subjectId)
                .topicId(topicId)
                .questionType(QuestionType.MCQ_SINGLE)
                .content("Câu hỏi nhận biết 1?")
                .difficultyLevel(DifficultyLevel.EASY)
                .build();
        questionBankRepository.save(q1);
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("POST /api/v1/exam-matrices: Tạo ma trận đề thi thành công")
    void testCreateExamMatrix_Success() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận kiểm tra giữa kỳ Vật lý 1")
                .description("Ma trận đề 20 câu giữa kỳ")
                .totalPoints(new BigDecimal("10.0"))
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(10)
                                .weightPercent(new BigDecimal("50.0"))
                                .build(),
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.MEDIUM)
                                .numQuestions(10)
                                .weightPercent(new BigDecimal("50.0"))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.matrixName").value("Ma trận kiểm tra giữa kỳ Vật lý 1"))
                .andExpect(jsonPath("$.data.totalQuestions").value(20))
                .andExpect(jsonPath("$.data.details", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/v1/exam-matrices: Lấy danh sách ma trận đề thi kèm thống kê")
    void testGetExamMatrices_Success() throws Exception {
        // Create one matrix first
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận cho Dropdown")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(5)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .param("subjectId", subjectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", not(empty())));

        // Also test query by classId
        mockMvc.perform(get("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .param("classId", classId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/v1/exam-matrices/{matrixId}: Xem chi tiết ma trận và phân bố Bloom")
    void testGetExamMatrixById_Success() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận phân bố Bloom")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(6)
                                .build(),
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.HARD)
                                .numQuestions(4)
                                .build()
                ))
                .build();

        String res = mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String matrixId = objectMapper.readTree(res).get("data").get("matrixId").asText();

        mockMvc.perform(get("/api/v1/exam-matrices/" + matrixId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.matrixId").value(matrixId))
                .andExpect(jsonPath("$.data.statistics.difficultyDistribution.EASY").value(6))
                .andExpect(jsonPath("$.data.statistics.difficultyDistribution.HARD").value(4));
    }

    @Test
    @DisplayName("PUT /api/v1/exam-matrices/{matrixId}: Sửa ma trận đề thi")
    void testUpdateExamMatrix_Success() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận ban đầu")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(5)
                                .build()
                ))
                .build();

        String res = mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String matrixId = objectMapper.readTree(res).get("data").get("matrixId").asText();

        UpdateExamMatrixDTO updateDTO = UpdateExamMatrixDTO.builder()
                .matrixName("Ma trận sau khi sửa")
                .description("Mô tả mới")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.MEDIUM)
                                .numQuestions(25)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/v1/exam-matrices/" + matrixId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrixName").value("Ma trận sau khi sửa"))
                .andExpect(jsonPath("$.data.totalQuestions").value(25))
                .andExpect(jsonPath("$.data.details", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/exam-matrices/{matrixId}/validate: Kiểm tra ngân hàng câu hỏi theo ma trận")
    void testValidateExamMatrix_Success() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận kiểm tra hợp lệ")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(1)
                                .build(),
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.HARD)
                                .numQuestions(100) // Bank doesn't have 100 hard questions
                                .build()
                ))
                .build();

        String res = mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String matrixId = objectMapper.readTree(res).get("data").get("matrixId").asText();

        mockMvc.perform(post("/api/v1/exam-matrices/" + matrixId + "/validate")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.matrixId").value(matrixId))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("DELETE /api/v1/exam-matrices/{matrixId}: Xóa ma trận đề thi chưa sử dụng")
    void testDeleteExamMatrix_Success() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận sắp bị xóa")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(3)
                                .build()
                ))
                .build();

        String res = mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String matrixId = objectMapper.readTree(res).get("data").get("matrixId").asText();

        mockMvc.perform(delete("/api/v1/exam-matrices/" + matrixId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa ma trận đề thi thành công"));

        // Verify it is gone
        mockMvc.perform(get("/api/v1/exam-matrices/" + matrixId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bảo mật: Sinh viên không được phép tạo ma trận đề thi (403 Forbidden)")
    void testStudentCannotCreateMatrix_Forbidden() throws Exception {
        CreateExamMatrixDTO createDTO = CreateExamMatrixDTO.builder()
                .subjectId(subjectId)
                .matrixName("Ma trận do sinh viên tạo trái phép")
                .details(List.of(
                        ExamMatrixDetailRequestDTO.builder()
                                .topicId(topicId)
                                .difficultyLevel(DifficultyLevel.EASY)
                                .numQuestions(5)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/exam-matrices")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isForbidden());
    }
}
