package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vatly1.example.app.JwtAuthServiceApp;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LearningMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private String topicId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        studentToken = signin("sv_an", "sv_an123456");

        String subjectRes = mockMvc.perform(get("/api/v1/subjects?page=0&size=1")
                .header("Authorization", "Bearer " + adminToken))
            .andReturn().getResponse().getContentAsString();
        String subjectId = objectMapper.readTree(subjectRes).get("data").get("content").get(0).get("subjectId").asText();

        String topicName = "Topic Material " + System.currentTimeMillis();
        String topicContent = """
            {
                "topicName": "%s",
                "orderIndex": 1
            }
            """.formatted(topicName);
        String topicRes = mockMvc.perform(post("/api/v1/subjects/" + subjectId + "/topics")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicContent))
            .andReturn().getResponse().getContentAsString();
        topicId = objectMapper.readTree(topicRes).get("data").get("topicId").asText();
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
    @DisplayName("MAT-01: Admin tạo tài liệu hợp lệ thành công")
    void createMaterial_asAdmin_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", "dummy pdf content".getBytes());
        
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + topicId + "/materials")
                .file(file)
                .param("title", "Tài liệu chương 1")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Tài liệu chương 1"))
            .andExpect(jsonPath("$.data.fileUrl").exists());
    }

    @Test
    @DisplayName("MAT-02: Upload file không đúng định dạng khai báo (khai type=PDF nhưng gửi file .exe) -> phải bị từ chối 400")
    void createMaterial_withInvalidFileFormat_exeExtension_returns400() throws Exception {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "virus_payload.exe",
                "application/x-msdownload",
                "MZexecutablebinarydata".getBytes()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + topicId + "/materials")
                .file(exeFile)
                .param("title", "Tài liệu giả mạo nguy hiểm")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MAT-03-MAGIC: Upload file .pdf nhưng nội dung thực tế là mã thực thi Windows MZ -> phải bị chặn 400 Bad Request")
    void createMaterial_disguisedExecutableWithPdfExtension_returns400() throws Exception {
        byte[] mzPayload = new byte[] {0x4D, 0x5A, 0x00, 0x00, 0x01, 0x02}; // Magic bytes 'M', 'Z'
        MockMultipartFile disguisedFile = new MockMultipartFile(
                "file",
                "malware_disguised.pdf",
                "application/pdf",
                mzPayload
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + topicId + "/materials")
                .file(disguisedFile)
                .param("title", "Tài liệu ngụy trang PDF")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MAT-03: Upload file vượt quá kích thước cho phép (> 50MB) -> phải bị từ chối")
    void createMaterial_withFileSizeExceedingLimit_returnsError() throws Exception {
        // Tạo file lớn 51MB (51 * 1024 * 1024 bytes)
        byte[] largeBytes = new byte[51 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "oversized.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + topicId + "/materials")
                .file(largeFile)
                .param("title", "Tài liệu quá dung lượng")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 400 && status != 413) {
                    throw new AssertionError("Kỳ vọng 400 Bad Request hoặc 413 Payload Too Large nhưng nhận được: " + status);
                }
            });
    }

    @Test
    @DisplayName("MAT-04: Upload tài liệu cho topicId không tồn tại -> 404 Not Found")
    void createMaterial_forNonExistentTopic_returns404() throws Exception {
        UUID fakeTopicId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "chapter.pdf", "application/pdf", "valid pdf".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + fakeTopicId + "/materials")
                .file(file)
                .param("title", "Tài liệu cho topic ảo")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MAT-05: Sinh viên KHÔNG được thấy tài liệu đang ở trạng thái pending/draft (chỉ thấy khi đã approved)")
    void getMaterials_asStudent_pendingMaterialsHiddenUntilApproved() throws Exception {
        // 1. Admin tạo tài liệu (mặc định approvalStatus = PENDING)
        MockMultipartFile file = new MockMultipartFile("file", "lecture_pending.pdf", "application/pdf", "lecture content".getBytes());
        String createRes = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/topics/" + topicId + "/materials")
                .file(file)
                .param("title", "Bài giảng chưa duyệt")
                .param("type", "PDF")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String materialId = objectMapper.readTree(createRes).get("data").get("materialId").asText();

        // 2. Sinh viên truy vấn danh sách materials của topic -> danh sách phải rỗng (không thấy bài pending)
        mockMvc.perform(get("/api/v1/topics/" + topicId + "/materials")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));

        // 3. Sinh viên cố truy cập trực tiếp bằng ID -> phải bị từ chối 403 Forbidden
        mockMvc.perform(get("/api/v1/topics/" + topicId + "/materials/" + materialId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());

        // 4. Admin duyệt tài liệu
        mockMvc.perform(put("/api/v1/topics/" + topicId + "/materials/" + materialId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        // 5. Sau khi duyệt, Sinh viên xem lại -> bây giờ đã thấy tài liệu
        mockMvc.perform(get("/api/v1/topics/" + topicId + "/materials")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].materialId").value(materialId));
    }
}
