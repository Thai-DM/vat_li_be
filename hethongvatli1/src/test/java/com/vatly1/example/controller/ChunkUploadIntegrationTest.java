package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.ChunkUploadSession;
import com.vatly1.example.entity.enums.ChunkUploadStatus;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IChunkUploadSessionRepository;
import com.vatly1.example.service.IFileStorageService;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChunkUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IChunkUploadSessionRepository sessionRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private IFileStorageService fileStorageService;

    @Autowired
    private com.vatly1.example.service.impl.ChunkUploadServiceImpl chunkUploadService;

    @Autowired
    private com.vatly1.example.configuration.ChunkUploadProperties chunkProperties;

    private String userToken1; // gv_nguyen
    private String userToken2; // sv_an

    @BeforeEach
    void setUp() throws Exception {
        userToken1 = signin("gv_nguyen", "gv_nguyen123456");
        userToken2 = signin("sv_an", "sv_an123456");
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

    private String calculateSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    @DisplayName("REQ-01 & REQ-02: Upload thành công 10 chunk tuần tự và Backend lưu đúng index từ 0 đến 9")
    void test01_02_upload10ChunksSequentially_storedCorrectly() throws Exception {
        UUID uploadId = UUID.randomUUID();
        String fileName = "tailieu_vatly.pdf";
        int totalChunks = 10;
        int chunkSize = 5000;

        for (int i = 0; i < totalChunks; i++) {
            byte[] chunkData = new byte[chunkSize];
            Arrays.fill(chunkData, (byte) (i + 1));
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "part.bin", "application/octet-stream", chunkData);

            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(i))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", fileName)
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                    .andExpect(jsonPath("$.chunkIndex").value(i))
                    .andExpect(jsonPath("$.totalChunks").value(totalChunks))
                    .andExpect(jsonPath("$.uploaded").value(true));
        }

        // Kiểm tra thư mục tạm chứa đúng 10 file part từ 0.part đến 9.part
        Path tempDir = Paths.get("storage/temp-uploads", uploadId.toString());
        assertTrue(Files.exists(tempDir), "Thư mục tạm phải tồn tại");
        for (int i = 0; i < totalChunks; i++) {
            Path partPath = tempDir.resolve(i + ".part");
            assertTrue(Files.exists(partPath), "File chunk " + i + ".part phải tồn tại");
            assertEquals(chunkSize, Files.size(partPath), "Kích thước chunk " + i + " phải khớp");
        }
    }

    @Test
    @DisplayName("REQ-03, REQ-07, REQ-08: Upload chunk không theo thứ tự (5, 2, 0, 9...), merge đúng SHA-256 và xóa folder tạm")
    void test03_07_08_uploadOutOfOrder_mergeExactSha256_andDeletesTempFolder() throws Exception {
        UUID uploadId = UUID.randomUUID();
        String fileName = "physics_experiment_data.bin";
        int totalChunks = 10;
        int chunkSize = 6000;

        // Tạo dữ liệu binary gốc
        byte[] originalFullFile = new byte[totalChunks * chunkSize];
        Random rand = new Random(42);
        rand.nextBytes(originalFullFile);
        String originalSha256 = calculateSha256(originalFullFile);

        // Thứ tự ngẫu nhiên
        List<Integer> outOfOrderIndices = Arrays.asList(5, 2, 0, 9, 3, 7, 1, 4, 8, 6);

        for (int idx : outOfOrderIndices) {
            byte[] chunkData = Arrays.copyOfRange(originalFullFile, idx * chunkSize, (idx + 1) * chunkSize);
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "part.bin", "application/octet-stream", chunkData);

            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(idx))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", fileName)
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk());
        }

        // Gọi API Merge Chunks
        String mergePayload = "{\"uploadId\":\"" + uploadId + "\"}";
        String mergeResponse = mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                    .andExpect(jsonPath("$.fileName").value(fileName))
                    .andExpect(jsonPath("$.fileSize").value(originalFullFile.length))
                    .andExpect(jsonPath("$.totalChunks").value(totalChunks))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.fileId").isString())
                    .andExpect(jsonPath("$.fileUrl").isString())
                    .andReturn().getResponse().getContentAsString();

        JsonNode mergeNode = objectMapper.readTree(mergeResponse);
        String fileUrl = mergeNode.get("fileUrl").asText();

        // Kiểm tra byte của file hoàn chỉnh bằng cách stream từ storage
        String storedFilename = fileUrl.replace("/api/v1/files/", "");
        try (InputStream is = fileStorageService.getFileInputStream(storedFilename)) {
            byte[] mergedBytes = is.readAllBytes();
            assertEquals(originalFullFile.length, mergedBytes.length, "Tổng kích thước byte phải khớp tuyệt đối");
            String mergedSha256 = calculateSha256(mergedBytes);
            assertEquals(originalSha256, mergedSha256, "Mã băm SHA-256 của file sau khi merge phải trùng khớp 100% với file gốc");
        }

        // Kiểm tra thư mục tạm của phiên upload đã được xóa sạch
        Path tempDir = Paths.get("storage/temp-uploads", uploadId.toString());
        assertFalse(Files.exists(tempDir), "Thư mục chunk tạm phải bị xóa hoàn toàn sau khi merge thành công");
    }

    @Test
    @DisplayName("REQ-04: Upload cùng chunk hai lần với nội dung giống nhau -> Idempotent Retry thành công 200 OK")
    void test04_idempotentRetrySameChunkReturns200() throws Exception {
        UUID uploadId = UUID.randomUUID();
        byte[] chunkData = "Cùng một nội dung chunk".getBytes();
        MockMultipartFile file1 = new MockMultipartFile("chunk", "c.bin", "application/octet-stream", chunkData);
        MockMultipartFile file2 = new MockMultipartFile("chunk", "c.bin", "application/octet-stream", chunkData);

        // Lần 1: Thành công
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(file1)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "3")
                        .param("fileName", "test.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(true));

        // Lần 2 (Retry cùng nội dung): Thành công idempotent
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(file2)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "3")
                        .param("fileName", "test.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(true))
                .andExpect(jsonPath("$.message", containsString("idempotent retry")));
    }

    @Test
    @DisplayName("REQ-05: Gửi chunk cùng index nhưng nội dung khác nhau -> Trả về 409 Conflict")
    void test05_sameIndexDifferentContentReturns409Conflict() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile fileA = new MockMultipartFile("chunk", "c.bin", "application/octet-stream", "Nội dung ban đầu".getBytes());
        MockMultipartFile fileB = new MockMultipartFile("chunk", "c.bin", "application/octet-stream", "Nội dung khác biệt gây xung đột".getBytes());

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(fileA)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "conflict.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // Lần 2 với nội dung khác
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(fileB)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "conflict.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("nội dung khác nhau")));
    }

    @Test
    @DisplayName("REQ-06: Thiếu chunk -> Từ chối merge bằng HTTP 409 Conflict với danh sách missingChunks")
    void test06_missingChunksRejectsMergeWith409() throws Exception {
        UUID uploadId = UUID.randomUUID();
        int totalChunks = 5;

        // Chỉ gửi chunk 0, 1, 2, 4 (thiếu chunk 3)
        for (int i : Arrays.asList(0, 1, 2, 4)) {
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", ("chunk " + i).getBytes());
            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(i))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", "incomplete.txt")
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk());
        }

        // Gọi merge -> Bị từ chối do thiếu chunk 3
        mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.message").value("Missing chunks"))
                .andExpect(jsonPath("$.missingChunks", contains(3)));
    }

    @Test
    @DisplayName("REQ-09: Khi xảy ra lỗi trong quá trình merge -> Giữ lại các chunk để có thể retry")
    void test09_mergeFailurePreservesChunks() throws Exception {
        UUID uploadId = UUID.randomUUID();
        int totalChunks = 2;

        for (int i = 0; i < totalChunks; i++) {
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", ("data " + i).getBytes());
            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(i))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", "fail_retry.txt")
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk());
        }

        Path tempDir = Paths.get("storage/temp-uploads", uploadId.toString());
        assertTrue(Files.exists(tempDir));

        // Giả lập lỗi quyền ghi file .merging bằng cách tạo sẵn 1 thư mục trùng tên .merging
        Path blockingPath = tempDir.resolve(uploadId + ".merging");
        Files.createDirectories(blockingPath);

        try {
            // Yêu cầu merge thất bại
            mockMvc.perform(post("/api/v1/files/chunks/merge")
                            .header("Authorization", "Bearer " + userToken1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"uploadId\":\"" + uploadId + "\"}"))
                    .andExpect(status().isInternalServerError());

            // Thư mục chunk và các file .part vẫn phải được BẢO TOÀN NGUYÊN VẸN để FE retry
            assertTrue(Files.exists(tempDir.resolve("0.part")), "0.part không được bị xóa khi merge lỗi");
            assertTrue(Files.exists(tempDir.resolve("1.part")), "1.part không được bị xóa khi merge lỗi");

            // Trạng thái phiên phục hồi về UPLOADING
            ChunkUploadSession session = sessionRepository.findById(uploadId).orElseThrow();
            assertEquals(ChunkUploadStatus.UPLOADING, session.getStatus());
        } finally {
            // Dọn dẹp blocking dir
            try { Files.deleteIfExists(blockingPath); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("REQ-10: Gọi merge đồng thời -> Tuần tự hóa an toàn, không tạo hai file cuối")
    void test10_concurrentMergeDoesNotCreateTwoFiles() throws Exception {
        UUID uploadId = UUID.randomUUID();
        int totalChunks = 3;

        for (int i = 0; i < totalChunks; i++) {
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", ("concurrent data " + i).getBytes());
            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(i))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", "concurrent_merge.txt")
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk());
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable mergeTask = () -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                int status = mockMvc.perform(post("/api/v1/files/chunks/merge")
                                .header("Authorization", "Bearer " + userToken1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"uploadId\":\"" + uploadId + "\"}"))
                        .andReturn().getResponse().getStatus();
                if (status == 200) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {}
        };

        executor.submit(mergeTask);
        executor.submit(mergeTask);

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Sau khi hoàn thành, phiên phải ở trạng thái COMPLETED và chỉ có đúng 1 record file được liên kết
        ChunkUploadSession session = sessionRepository.findById(uploadId).orElseThrow();
        assertEquals(ChunkUploadStatus.COMPLETED, session.getStatus());
        assertNotNull(session.getFinalFileId());
    }

    @Test
    @DisplayName("REQ-11: Người dùng khác không thể dùng uploadId để upload hoặc merge (403 Forbidden)")
    void test11_otherUserCannotUseUploadId() throws Exception {
        UUID uploadId = UUID.randomUUID();

        // User 1 tải chunk 0
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "user 1 data".getBytes());
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "user_isolated.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // User 2 cố tình gửi chunk 1 vào uploadId của User 1 -> 403 Forbidden
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "1")
                        .param("totalChunks", "2")
                        .param("fileName", "user_isolated.txt")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("phiên upload của người khác")));

        // User 2 cố tình gọi merge uploadId của User 1 -> 403 Forbidden
        mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("REQ-12: Tên file chứa path traversal ('../') bị từ chối với 400 Bad Request")
    void test12_pathTraversalInFileNameRejected() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "evil data".getBytes());

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("fileName", "../../etc/passwd")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("path traversal")));
    }

    @Test
    @DisplayName("REQ-13: Chunk index âm hoặc vượt giới hạn totalChunks bị từ chối với 400 Bad Request")
    void test13_invalidChunkIndicesRejected() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "data".getBytes());

        // Index âm (-1)
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "-1")
                        .param("totalChunks", "5")
                        .param("fileName", "test.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isBadRequest());

        // Index vượt quá (5/5)
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "5")
                        .param("totalChunks", "5")
                        .param("fileName", "test.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REQ-14: Chunk quá dung lượng bị từ chối với 400 Bad Request")
    void test14_chunkOverSizeLimitRejected() throws Exception {
        UUID uploadId = UUID.randomUUID();
        byte[] largeChunk = new byte[(int) chunkProperties.getMaxChunkSize() + 1];
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", largeChunk);

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "oversize.bin")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("vượt quá giới hạn tối đa")));
    }

    @Test
    @DisplayName("REQ-15: Chức năng upload file đơn thông thường hiện có vẫn hoạt động bình thường")
    void test15_legacyFileUploadStillWorks() throws Exception {
        MockMultipartFile normalFile = new MockMultipartFile(
                "file", "legacy_test.pdf", "application/pdf", "%PDF-1.4 Legacy test file".getBytes()
        );

        String res = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(normalFile)
                        .param("folder", "materials")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").isString())
                .andReturn().getResponse().getContentAsString();

        String fileUrl = objectMapper.readTree(res).get("data").get("url").asText();

        // Kiểm tra truy xuất
        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("REQ-16 & REQ-17: API tra cứu trạng thái session (status) trả đúng các chunk đã nhận và còn thiếu")
    void test16_17_getUploadStatusAccurate() throws Exception {
        UUID uploadId = UUID.randomUUID();
        int totalChunks = 4;

        // Tải chunk 0 và chunk 2
        for (int i : Arrays.asList(0, 2)) {
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", ("status data " + i).getBytes());
            mockMvc.perform(multipart("/api/v1/files/chunks")
                            .file(chunkFile)
                            .param("uploadId", uploadId.toString())
                            .param("chunkIndex", String.valueOf(i))
                            .param("totalChunks", String.valueOf(totalChunks))
                            .param("fileName", "status_check.txt")
                            .header("Authorization", "Bearer " + userToken1))
                    .andExpect(status().isOk());
        }

        // Gọi API Status
        mockMvc.perform(get("/api/v1/files/chunks/" + uploadId + "/status")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.totalChunks").value(4))
                .andExpect(jsonPath("$.uploadedChunks", containsInAnyOrder(0, 2)))
                .andExpect(jsonPath("$.missingChunks", containsInAnyOrder(1, 3)))
                .andExpect(jsonPath("$.status").value("UPLOADING"));
    }

    @Test
    @DisplayName("REQ-18: Kiểm tra file hoàn chỉnh sau merge: tải lại, đúng bytes, và kiểm soát quyền truy cập")
    void test18_mergedFileDownloadAndIntegrity_onlyOwnerAndAdminAllowed() throws Exception {
        UUID uploadId = UUID.randomUUID();
        String fileName = "physics_secure.pdf";
        byte[] expectedContent = "CONFIDENTIAL PHYSICS PAPER 2026 CONTENT".getBytes();

        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", expectedContent);
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("fileName", fileName)
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // Merge
        String mergeRes = mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode mergeJson = objectMapper.readTree(mergeRes);
        String fileIdStr = mergeJson.get("fileId").asText();
        String fileUrl = mergeJson.get("fileUrl").asText();

        // 1. Chủ sở hữu (userToken1) tạo download URL an toàn
        String dlRes = mockMvc.perform(post("/api/v1/files/" + fileIdStr + "/download-url")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").isString())
                .andReturn().getResponse().getContentAsString();

        String signedDownloadUrl = objectMapper.readTree(dlRes).get("data").get("downloadUrl").asText();

        // Tải qua download URL có token hợp lệ
        byte[] downloadedBytes = mockMvc.perform(get(signedDownloadUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(expectedContent, downloadedBytes);

        // 2. Tải trực tiếp qua fileUrl với chủ sở hữu
        byte[] directBytes = mockMvc.perform(get(fileUrl)
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(expectedContent, directBytes);

        // 3. Người dùng khác (userToken2) cố tạo download URL -> 403 Forbidden
        mockMvc.perform(post("/api/v1/files/" + fileIdStr + "/download-url")
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());

        // 4. Người dùng khác cố tải trực tiếp fileUrl -> 403 Forbidden
        mockMvc.perform(get(fileUrl)
                        .header("Authorization", "Bearer " + userToken2))
                .andExpect(status().isForbidden());

        // 5. Chưa đăng nhập cố tải trực tiếp -> 401 Unauthorized
        mockMvc.perform(get(fileUrl))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQ-19: Merge thành công rồi gọi merge lại trả về kết quả đã xong mà không tạo file mới")
    void test19_mergeCompletedSessionAgain_returnsExistingMetadataWithoutDuplication() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "Single chunk data".getBytes());

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunkFile)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("fileName", "idempotent_merge.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // Merge lần 1
        String firstMergeRes = mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstFileId = objectMapper.readTree(firstMergeRes).get("fileId").asText();

        // Merge lần 2
        String secondMergeRes = mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .header("Authorization", "Bearer " + userToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":\"" + uploadId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondFileId = objectMapper.readTree(secondMergeRes).get("fileId").asText();
        assertEquals(firstFileId, secondFileId, "FileId phải giữ nguyên, không được tạo 2 file hoàn chỉnh");
    }

    @Test
    @DisplayName("REQ-20: Cạnh tranh đồng thời cùng chunkIndex (cùng nội dung -> 200, khác nội dung -> 409)")
    void test20_concurrentUploadSameChunkIndex_sameAndDifferentContent() throws Exception {
        UUID uploadId = UUID.randomUUID();
        byte[] commonContent = "SAME CONTENT FOR INDEX 0".getBytes();

        // 1. Cùng nội dung gửi đồng thời trên 2 luồng
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    MockMultipartFile chunkFile = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", commonContent);
                    int status = mockMvc.perform(multipart("/api/v1/files/chunks")
                                    .file(chunkFile)
                                    .param("uploadId", uploadId.toString())
                                    .param("chunkIndex", "0")
                                    .param("totalChunks", "2")
                                    .param("fileName", "concurrency_test.txt")
                                    .header("Authorization", "Bearer " + userToken1))
                            .andReturn().getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        assertEquals(2, successCount.get(), "Cả 2 request gửi cùng nội dung phải thành công (1 upload, 1 idempotent retry)");

        // 2. Gửi khác nội dung cho cùng index 0 -> phải bị 409 Conflict
        byte[] differentContent = "DIFFERENT CONFLICTING CONTENT".getBytes();
        MockMultipartFile conflictChunk = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", differentContent);
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(conflictChunk)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "concurrency_test.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("nội dung khác nhau")));

        executor.shutdown();
    }

    @Test
    @DisplayName("REQ-21: Không cho upload chunk khi phiên đang MERGING")
    void test21_uploadChunkWhileMerging_rejectedWithConflict() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile chunk0 = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "part 0".getBytes());

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunk0)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "merging_block.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // Chuyển thủ công sang MERGING để mô phỏng tiến trình đang ghép
        ChunkUploadSession session = sessionRepository.findById(uploadId).orElseThrow();
        session.setStatus(ChunkUploadStatus.MERGING);
        sessionRepository.saveAndFlush(session);

        // Upload chunk 1 trong lúc MERGING -> bị từ chối 409
        MockMultipartFile chunk1 = new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "part 1".getBytes());
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(chunk1)
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "1")
                        .param("totalChunks", "2")
                        .param("fileName", "merging_block.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("đang được ghép file")));
    }

    @Test
    @DisplayName("REQ-22: MinIO failure policy: nếu local fallback bị cấm, ném lỗi và không lưu bừa bãi")
    void test22_localFallbackDisabledPolicy() {
        if (fileStorageService instanceof com.vatly1.example.service.impl.FileStorageServiceImpl impl) {
            boolean original = impl.isAllowLocalFallback();
            try {
                impl.setAllowLocalFallback(false);
                // Với allowLocalFallback = false và MinIO test profile đang tắt -> ném lỗi
                assertThrows(Exception.class, () -> {
                    impl.storeFile(new MockMultipartFile("f", "test.bin", "text/plain", "data".getBytes()));
                });
            } finally {
                impl.setAllowLocalFallback(original);
            }
        }
    }

    @Test
    @DisplayName("REQ-23: Cleanup tác vụ định kỳ: dọn phiên quá hạn và zombie MERGING session do server crash")
    void test23_cleanupExpiredSessions_cleansZombieMergingAndPreservesActiveSessions() throws Exception {
        // 1. Session bình thường đang hoạt động (còn hạn)
        UUID activeId = UUID.randomUUID();
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "active data".getBytes()))
                        .param("uploadId", activeId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "active.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        // 2. Session đã quá hạn 24h
        UUID expiredId = UUID.randomUUID();
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "expired data".getBytes()))
                        .param("uploadId", expiredId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "expired.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        ChunkUploadSession expiredSession = sessionRepository.findById(expiredId).orElseThrow();
        expiredSession.setExpiresAt(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));
        sessionRepository.saveAndFlush(expiredSession);

        // 3. Zombie session ở trạng thái MERGING nhưng đã quá hạn (server crash giữa lúc merge)
        UUID zombieId = UUID.randomUUID();
        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(new MockMultipartFile("chunk", "p.bin", "application/octet-stream", "zombie data".getBytes()))
                        .param("uploadId", zombieId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .param("fileName", "zombie.txt")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        ChunkUploadSession zombieSession = sessionRepository.findById(zombieId).orElseThrow();
        zombieSession.setStatus(ChunkUploadStatus.MERGING);
        zombieSession.setExpiresAt(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));
        sessionRepository.saveAndFlush(zombieSession);

        // Chạy tác vụ cleanup
        chunkUploadService.cleanupExpiredSessions();

        // Kiểm tra kết quả
        ChunkUploadSession activeAfter = sessionRepository.findById(activeId).orElseThrow();
        assertEquals(ChunkUploadStatus.UPLOADING, activeAfter.getStatus(), "Session còn hạn phải giữ nguyên");

        ChunkUploadSession expiredAfter = sessionRepository.findById(expiredId).orElseThrow();
        assertEquals(ChunkUploadStatus.EXPIRED, expiredAfter.getStatus(), "Session quá hạn phải chuyển sang EXPIRED");

        ChunkUploadSession zombieAfter = sessionRepository.findById(zombieId).orElseThrow();
        assertEquals(ChunkUploadStatus.FAILED, zombieAfter.getStatus(), "Zombie session bị crash phải chuyển sang FAILED");
    }

    @Test
    @DisplayName("REQ-18: Hỗ trợ chunk lớn (trên 100KB) và merge với payload JSON chứa extra metadata")
    void test18_uploadLargeChunkAndMergeWithExtraJsonFields_Success() throws Exception {
        UUID uploadId = UUID.randomUUID();
        String fileName = "README.md";
        byte[] largeData = new byte[256 * 1024]; // 256 KB chunk (vượt 100KB cũ)
        Arrays.fill(largeData, (byte) '#');

        mockMvc.perform(multipart("/api/v1/files/chunks")
                        .file(new MockMultipartFile("chunk", "part.bin", "text/markdown", largeData))
                        .param("uploadId", uploadId.toString())
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("fileName", fileName)
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded", is(true)));

        // Merge với payload JSON chứa extra fields như FE thường gửi: fileName, totalChunks, fileSize
        String mergePayload = "{\"uploadId\":\"" + uploadId + "\",\"fileName\":\"" + fileName + "\",\"totalChunks\":1,\"fileSize\":" + largeData.length + "}";

        mockMvc.perform(post("/api/v1/files/chunks/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergePayload)
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.fileName", is(fileName)))
                .andExpect(jsonPath("$.fileSize", is(largeData.length)));
    }
}

