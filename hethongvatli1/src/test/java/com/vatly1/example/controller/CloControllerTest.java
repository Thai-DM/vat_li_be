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
public class CloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String instructorToken;
    private String studentToken;
    private String subjectId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        studentToken = signin("sv_an", "sv_an123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=10&isActive=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(subjectRes).get("data").get("content");
        for (JsonNode node : content) {
            if (node.has("isActive") && node.get("isActive").asBoolean()) {
                subjectId = node.get("subjectId").asText();
                break;
            }
        }
        if (subjectId == null && content.size() > 0) {
            subjectId = content.get(0).get("subjectId").asText();
        }
    }

    private String signin(String username, String password) throws Exception {
        String req = """
            {
                "username": "%s",
                "password": "%s"
            }
            """.formatted(username, password);

        String res = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("Admin tạo CLO thành công (201 Created)")
    void testCreateClo_Admin_Success() throws Exception {
        String cloCode = "CLO_CTRL_" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
            {
                "cloCode": "%s",
                "description": "Hieu cac dinh luat chuyen dong",
                "orderIndex": 1
            }
            """.formatted(cloCode);

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cloCode").value(cloCode))
                .andExpect(jsonPath("$.data.orderIndex").value(1))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("Giảng viên không có quyền tạo CLO (403 Forbidden)")
    void testCreateClo_Instructor_Forbidden() throws Exception {
        String cloCode = "CLO_INS_" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
            {
                "cloCode": "%s",
                "description": "Van dung dinh luat New-ton",
                "orderIndex": 2
            }
            """.formatted(cloCode);

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sinh viên không có quyền tạo CLO (403 Forbidden)")
    void testCreateClo_Student_Forbidden() throws Exception {
        String body = """
            {
                "cloCode": "CLO_DENIED",
                "description": "Khong co quyen",
                "orderIndex": 1
            }
            """;

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tạo CLO trùng mã trong cùng môn học báo lỗi 409 Conflict")
    void testCreateClo_DuplicateCode_Conflict() throws Exception {
        String cloCode = "CLO_DUP_" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
            {
                "cloCode": "%s",
                "description": "CLO goc",
                "orderIndex": 1
            }
            """.formatted(cloCode);

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Sinh viên được phép xem danh sách CLO của môn học (200 OK)")
    void testGetClosBySubject_StudentAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Cập nhật thông tin CLO thành công (200 OK)")
    void testUpdateClo_Success() throws Exception {
        String cloCode = "CLO_UPD_" + UUID.randomUUID().toString().substring(0, 6);
        String createBody = """
            {
                "cloCode": "%s",
                "description": "Mo ta ban dau",
                "orderIndex": 1
            }
            """.formatted(cloCode);

        String res = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String cloId = objectMapper.readTree(res).get("data").get("cloId").asText();

        String updateBody = """
            {
                "cloCode": "%s",
                "description": "Mo ta da cap nhat",
                "orderIndex": 5
            }
            """.formatted(cloCode);

        mockMvc.perform(put("/api/v1/clos/" + cloId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Mo ta da cap nhat"))
                .andExpect(jsonPath("$.data.orderIndex").value(5));
    }

    @Test
    @DisplayName("Bật/tắt trạng thái hoạt động của CLO (200 OK)")
    void testToggleCloStatus_Success() throws Exception {
        String cloCode = "CLO_TOG_" + UUID.randomUUID().toString().substring(0, 6);
        String createBody = """
            {
                "cloCode": "%s",
                "description": "CLO kiem thu bat tat",
                "orderIndex": 1
            }
            """.formatted(cloCode);

        String res = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String cloId = objectMapper.readTree(res).get("data").get("cloId").asText();

        mockMvc.perform(patch("/api/v1/clos/" + cloId + "/toggle-status?isActive=false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        mockMvc.perform(patch("/api/v1/clos/" + cloId + "/toggle-status?isActive=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("Admin xóa CLO thành công, Giảng viên bị chặn (403 Forbidden)")
    void testDeleteClo_RbacControls() throws Exception {
        String cloCode = "CLO_DEL_" + UUID.randomUUID().toString().substring(0, 6);
        String createBody = """
            {
                "cloCode": "%s",
                "description": "CLO se bi xoa",
                "orderIndex": 1
            }
            """.formatted(cloCode);

        String res = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/clos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String cloId = objectMapper.readTree(res).get("data").get("cloId").asText();

        // Giảng viên thử xóa -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/clos/" + cloId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        // Admin xóa -> 200 OK
        mockMvc.perform(delete("/api/v1/clos/" + cloId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("CLO deleted successfully"));

        // Lấy lại sau khi xóa -> 404 Not Found
        mockMvc.perform(get("/api/v1/clos/" + cloId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
