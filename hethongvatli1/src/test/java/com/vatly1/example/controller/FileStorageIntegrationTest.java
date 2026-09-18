package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FileStorageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String bodyContent = "{\"username\":\"admin\",\"password\":\"admin123456\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        adminToken = objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("FILE-01: Tải lên hình ảnh hợp lệ (PNG) thành công, trả về URL truy cập")
    void uploadImage_asAdmin_success() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "diagram.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}
        );

        String response = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(imageFile)
                        .param("folder", "images")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").isString())
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();

        String fileUrl = objectMapper.readTree(response).get("data").get("url").asText();

        // Kiểm tra truy xuất file vừa upload
        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("FILE-02: Tải lên tài liệu PDF thành công và stream lại với đúng MediaType")
    void uploadDocument_asAdmin_success() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "bai_giang.pdf", "application/pdf", "%PDF-1.4 Mock document content".getBytes()
        );

        String response = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(pdfFile)
                        .param("folder", "materials")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").isString())
                .andReturn().getResponse().getContentAsString();

        String fileUrl = objectMapper.readTree(response).get("data").get("url").asText();

        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("FILE-03: Tải lên tệp rỗng -> Bị từ chối bằng 400 Bad Request")
    void uploadFile_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FILE-04: Truy xuất tệp không tồn tại -> Trả về 404 Not Found an toàn")
    void getFile_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/files/non_existent_file_123456.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FILE-05: Người dùng chưa đăng nhập tải file -> Bị chặn 403 Forbidden")
    void uploadFile_unauthenticated_returns403() throws Exception {
        MockMultipartFile testFile = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(testFile))
                .andExpect(status().isForbidden());
    }
}
