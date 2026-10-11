package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChapterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private String subjectId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?isActive=true&page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(subjectRes);
        subjectId = root.get("data").get("content").get(0).get("subjectId").asText();
    }

    private String signin(String username, String password) throws Exception {
        String body = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);

        String res = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(res).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("Admin tạo và lấy danh sách chương thành công")
    void testCreateAndGetChapters() throws Exception {
        String chapterCode = "CH_" + System.currentTimeMillis();
        String createJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "%s",
                    "chapterName": "Chương Kiểm Thử REST API",
                    "description": "Mô tả chi tiết chương",
                    "orderIndex": 1,
                    "isActive": true
                }
                """.formatted(subjectId, chapterCode);

        // 1. Admin tạo Chapter -> 201 Created
        String createRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.chapterCode").value(chapterCode))
                .andExpect(jsonPath("$.data.chapterName").value("Chương Kiểm Thử REST API"))
                .andReturn().getResponse().getContentAsString();

        String chapterId = objectMapper.readTree(createRes).get("data").get("chapterId").asText();

        // 2. Lấy danh sách Chapter theo Subject -> 200 OK
        mockMvc.perform(get("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 3. Lấy chi tiết Chapter -> 200 OK
        mockMvc.perform(get("/api/v1/chapters/" + chapterId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterId").value(chapterId));
    }

    @Test
    @DisplayName("Student bị chặn khi tạo Chapter (403 Forbidden)")
    void testStudentForbiddenFromCreatingChapter() throws Exception {
        String createJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "CH_FORBIDDEN",
                    "chapterName": "Chương Sinh Viên Không Được Tạo"
                }
                """.formatted(subjectId);

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tạo trùng mã chương trả về 409 Conflict")
    void testDuplicateChapterCodeReturnsConflict() throws Exception {
        String chapterCode = "CH_DUP_" + System.currentTimeMillis();
        String createJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "%s",
                    "chapterName": "Chương Ban Đầu"
                }
                """.formatted(subjectId, chapterCode);

        // Tạo lần 1 -> 201
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated());

        // Tạo lần 2 cùng code -> 409 Conflict
        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cập nhật thông tin và trạng thái Chapter")
    void testUpdateChapterAndStatus() throws Exception {
        String chapterCode = "CH_UPD_" + System.currentTimeMillis();
        String createJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "%s",
                    "chapterName": "Tên Cũ"
                }
                """.formatted(subjectId, chapterCode);

        String createRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String chapterId = objectMapper.readTree(createRes).get("data").get("chapterId").asText();

        // Cập nhật tên chương
        String updateJson = """
                {
                    "chapterName": "Tên Đã Cập Nhật"
                }
                """;
        mockMvc.perform(put("/api/v1/chapters/" + chapterId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterName").value("Tên Đã Cập Nhật"));

        // Cập nhật trạng thái status sang false
        mockMvc.perform(put("/api/v1/chapters/" + chapterId + "/status?isActive=false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    @DisplayName("Gán và gỡ Chapter cho Topic qua TopicController")
    void testAssignAndUnassignChapterToTopic() throws Exception {
        // 1. Tạo Chapter
        String chapterCode = "CH_ASSIGN_" + System.currentTimeMillis();
        String createChapJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "%s",
                    "chapterName": "Chương Cho Topic"
                }
                """.formatted(subjectId, chapterCode);

        String chapRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createChapJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String chapterId = objectMapper.readTree(chapRes).get("data").get("chapterId").asText();

        // 2. Tạo Topic không có chapter
        String createTopicJson = """
                {
                    "subjectId": "%s",
                    "topicName": "Topic Test Gán Chương %s"
                }
                """.formatted(subjectId, System.currentTimeMillis());

        String topicRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTopicJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String topicId = objectMapper.readTree(topicRes).get("data").get("topicId").asText();

        // 3. Gán Chapter vào Topic -> 200 OK
        String assignJson = """
                {
                    "chapterId": "%s"
                }
                """.formatted(chapterId);

        mockMvc.perform(put("/api/v1/topics/" + topicId + "/assign-chapter")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterId").value(chapterId));

        // 4. Lấy danh sách Topic thuộc Chapter -> Phải chứa Topic vừa gán
        mockMvc.perform(get("/api/v1/chapters/" + chapterId + "/topics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicId").value(topicId));

        // 5. Gỡ Chapter khỏi Topic (gửi chapterId = null) -> 200 OK
        String unassignJson = """
                {
                    "chapterId": null
                }
                """;
        mockMvc.perform(put("/api/v1/topics/" + topicId + "/assign-chapter")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unassignJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterId").isEmpty());

        // 6. Xóa Chapter khi đã gỡ hết Topic -> 200 OK
        mockMvc.perform(delete("/api/v1/chapters/" + chapterId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reorder danh sách Topic trong Chapter")
    void testReorderTopicsInChapter() throws Exception {
        // Tạo Chapter
        String chapterCode = "CH_ORD_" + System.currentTimeMillis();
        String createChapJson = """
                {
                    "subjectId": "%s",
                    "chapterCode": "%s",
                    "chapterName": "Chương Reorder API"
                }
                """.formatted(subjectId, chapterCode);

        String chapRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/chapters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createChapJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String chapterId = objectMapper.readTree(chapRes).get("data").get("chapterId").asText();

        // Tạo 2 Topics thuộc Chapter
        String t1Res = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterId": "%s",
                            "topicName": "Topic 1 %s",
                            "orderIndex": 1
                        }
                        """.formatted(subjectId, chapterId, System.currentTimeMillis())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String t1Id = objectMapper.readTree(t1Res).get("data").get("topicId").asText();

        String t2Res = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "subjectId": "%s",
                            "chapterId": "%s",
                            "topicName": "Topic 2 %s",
                            "orderIndex": 2
                        }
                        """.formatted(subjectId, chapterId, System.currentTimeMillis() + 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String t2Id = objectMapper.readTree(t2Res).get("data").get("topicId").asText();

        // Đảo thứ tự: [t2Id, t1Id]
        String reorderJson = """
                {
                    "topicIds": ["%s", "%s"]
                }
                """.formatted(t2Id, t1Id);

        mockMvc.perform(put("/api/v1/chapters/" + chapterId + "/topics/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicId").value(t2Id))
                .andExpect(jsonPath("$.data[0].orderIndex").value(1))
                .andExpect(jsonPath("$.data[1].topicId").value(t1Id))
                .andExpect(jsonPath("$.data[1].orderIndex").value(2));
    }
}
