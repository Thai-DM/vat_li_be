package com.vatly1.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.MaterialProgressRepository;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MaterialLearningProgressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private MaterialProgressRepository materialProgressRepository;

    private String studentToken;
    private String adminToken;
    private Class clazz;
    private Topic topic;
    private LearningMaterial material1;
    private LearningMaterial material2;
    private UUID unenrolledClassId;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = signin("sv_an", "sv_an123456");
        adminToken = signin("admin", "admin123456");

        clazz = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();

        topic = topicRepository.save(Topic.builder()
                .subjectId(clazz.getSubjectId())
                .topicName("Chủ đề Khảo sát tiến độ học liệu " + UUID.randomUUID())
                .orderIndex(1)
                .build());

        material1 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(clazz.getSubjectId())
                .topicId(topic.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Slide bài giảng điện tử chương Cơ học")
                .type(MaterialType.PDF)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        material2 = learningMaterialRepository.save(LearningMaterial.builder()
                .subjectId(clazz.getSubjectId())
                .topicId(topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(clazz.getClassId())
                .title("Video hướng dẫn giải bài tập thực hành")
                .type(MaterialType.VIDEO)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Class unenrolledClass = classRepository.save(Class.builder()
                .subjectId(clazz.getSubjectId())
                .classCode("UNENROLLED-MAT-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClassStatus.ACTIVE)
                .maxStudents(20)
                .build());
        unenrolledClassId = unenrolledClass.getClassId();
    }

    private String signin(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String res = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("MAT-01: Cập nhật tiến độ học liệu cụ thể (40%) -> status = IN_PROGRESS, ghi nhận timeSpent")
    void test01_UpdateMaterialProgress_InProgress_Success() throws Exception {
        String payload = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 40.0,
                "timeSpentSeconds": 150
            }
            """.formatted(material1.getMaterialId(), clazz.getClassId());

        mockMvc.perform(put("/api/v1/students/me/progress/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialId").value(material1.getMaterialId().toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.progressPercent").value(40.0))
                .andExpect(jsonPath("$.data.timeSpentSeconds").value(150));
    }

    @Test
    @DisplayName("MAT-02: Hoàn thành 100% học liệu 1 và 2 -> Tự động tính toán lại tiến độ Topic từ 50% lên 100% COMPLETED")
    void test02_CompleteMaterials_AutoRecalculatesTopicProgress() throws Exception {
        // 1. Hoàn thành học liệu 1 (1/2 tài liệu)
        String payload1 = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 100.0,
                "timeSpentSeconds": 300
            }
            """.formatted(material1.getMaterialId(), clazz.getClassId());

        mockMvc.perform(put("/api/v1/students/me/progress/materials/" + material1.getMaterialId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // Kiểm tra tiến độ Topic: Có 2 học liệu, hoàn thành 1 -> 50% IN_PROGRESS
        mockMvc.perform(get("/api/v1/students/me/progress?classId=" + clazz.getClassId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].progressPercent").value(contains(50.0)))
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].status").value(contains("IN_PROGRESS")))
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].totalMaterials").value(contains(2)))
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].completedMaterials").value(contains(1)));

        // 2. Hoàn thành nốt học liệu 2 (2/2 tài liệu)
        String payload2 = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 100.0,
                "timeSpentSeconds": 450
            }
            """.formatted(material2.getMaterialId(), clazz.getClassId());

        mockMvc.perform(put("/api/v1/students/me/progress/materials/" + material2.getMaterialId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Kiểm tra tiến độ Topic sau khi học xong cả 2 -> 100% COMPLETED
        mockMvc.perform(get("/api/v1/students/me/progress?classId=" + clazz.getClassId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].progressPercent").value(contains(100.0)))
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].status").value(contains("COMPLETED")))
                .andExpect(jsonPath("$.data[?(@.topicId == '" + topic.getTopicId() + "')].completedMaterials").value(contains(2)));
    }

    @Test
    @DisplayName("MAT-03: API /api/v1/students/me/progress/materials trả về danh sách tiến độ chi tiết từng học liệu")
    void test03_GetMyMaterialProgress_ReturnsList() throws Exception {
        // Cập nhật học liệu 1
        String payload1 = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 100.0,
                "timeSpentSeconds": 200
            }
            """.formatted(material1.getMaterialId(), clazz.getClassId());

        mockMvc.perform(put("/api/v1/students/me/progress/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk());

        // Gọi API lấy danh sách học liệu đã học
        mockMvc.perform(get("/api/v1/students/me/progress/materials?classId=" + clazz.getClassId() + "&topicId=" + topic.getTopicId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].materialId").value(material1.getMaterialId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("MAT-04: Cập nhật qua /api/v1/students/me/progress với trường materialId -> cập nhật cả học liệu và topic")
    void test04_UpdateProgressWithMaterialId_Success() throws Exception {
        String payload = """
            {
                "classId": "%s",
                "materialId": "%s",
                "progressPercent": 100.0,
                "timeSpentSeconds": 60
            }
            """.formatted(clazz.getClassId(), material1.getMaterialId());

        mockMvc.perform(put("/api/v1/students/me/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedMaterials").value(1))
                .andExpect(jsonPath("$.data.completedMaterialIds", hasItem(material1.getMaterialId().toString())));
    }

    @Test
    @DisplayName("MAT-05: Sinh viên chưa ghi danh vào lớp cập nhật tiến độ học liệu -> 403 Forbidden")
    void test05_UnenrolledStudent_Forbidden() throws Exception {
        String payload = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 100.0
            }
            """.formatted(material1.getMaterialId(), unenrolledClassId);

        mockMvc.perform(put("/api/v1/students/me/progress/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MAT-06: Cập nhật học liệu không tồn tại -> 404 Not Found")
    void test06_NonExistentMaterial_NotFound() throws Exception {
        String payload = """
            {
                "materialId": "%s",
                "classId": "%s",
                "progressPercent": 50.0
            }
            """.formatted(UUID.randomUUID(), clazz.getClassId());

        mockMvc.perform(put("/api/v1/students/me/progress/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }
}
