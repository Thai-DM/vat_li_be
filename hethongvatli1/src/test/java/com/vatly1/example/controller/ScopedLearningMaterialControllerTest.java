package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IFileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
public class ScopedLearningMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassEnrollmentRepository enrollmentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private LearningMaterialRepository materialRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private IFileStorageService fileStorageService;

    private String adminToken;
    private String instructorToken;
    private String otherInstructorToken;
    private String studentAnToken;
    private String studentDungToken;

    private Subject phy101;
    private Subject phy102;
    private Class classPhy101_01;
    private Class classPhy101_02;
    private Topic phy101Topic;
    private Topic phy102Topic;

    private final List<UUID> createdMaterialIds = new ArrayList<>();
    private final List<UUID> createdUploadIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        adminToken = signin("admin", "admin123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");
        otherInstructorToken = signin("gv_tran", "gv_tran123456");
        studentAnToken = signin("sv_an", "sv_an123456");
        studentDungToken = signin("sv_dung", "sv_dung123456");

        phy101 = subjectRepository.findAll().stream()
                .filter(s -> "PHY101".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        phy102 = subjectRepository.findAll().stream()
                .filter(s -> "PHY102".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        classPhy101_01 = classRepository.findByClassCode("PHY101-01").orElseThrow();
        classPhy101_02 = classRepository.findByClassCode("PHY101-02").orElseThrow();

        phy101Topic = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy101.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy101.getSubjectId())
                        .topicName("Ctrl Test Topic PHY101")
                        .orderIndex(1)
                        .build()));

        phy102Topic = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy102.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy102.getSubjectId())
                        .topicName("Ctrl Test Topic PHY102")
                        .orderIndex(1)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdMaterialIds) {
            try {
                materialRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdMaterialIds.clear();

        for (UUID id : createdUploadIds) {
            try {
                fileUploadRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdUploadIds.clear();
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
    @DisplayName("API-01: Giảng viên phụ trách tạo học liệu riêng theo lớp thành công (201 Created, PENDING)")
    void testCreateClassMaterial_byAssignedInstructor_status201() throws Exception {
        String res = mockMvc.perform(multipart("/api/v1/classes/" + classPhy101_01.getClassId() + "/topics/" + phy101Topic.getTopicId() + "/materials")
                        .param("title", "Bài tập ôn tập lớp 1")
                        .param("type", "MARKDOWN")
                        .param("contentText", "# Nội dung bài tập lớp 1")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("CLASS"))
                .andExpect(jsonPath("$.data.classId").value(classPhy101_01.getClassId().toString()))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(res);
        if (root.has("data") && root.get("data").has("materialId")) {
            createdMaterialIds.add(UUID.fromString(root.get("data").get("materialId").asText()));
        }
    }

    @Test
    @DisplayName("API-02: Giảng viên không phụ trách lớp bị chặn tạo học liệu riêng (403 Forbidden)")
    void testCreateClassMaterial_byUnassignedInstructor_status403() throws Exception {
        mockMvc.perform(multipart("/api/v1/classes/" + classPhy101_01.getClassId() + "/topics/" + phy101Topic.getTopicId() + "/materials")
                        .param("title", "Bài tập trái phép")
                        .param("type", "MARKDOWN")
                        .param("contentText", "# Không hợp lệ")
                        .header("Authorization", "Bearer " + otherInstructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API-03: Sinh viên lớp A lấy danh sách học liệu theo lớp 200 OK (thấy SHARED + CLASS của lớp A)")
    void testGetClassMaterials_byStudentInClass_status200() throws Exception {
        LearningMaterial sharedMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Shared Phổ biến")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedMat.getMaterialId());

        LearningMaterial class1Mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Tài liệu riêng Lớp 1")
                .type(MaterialType.MARKDOWN)
                .contentText("# Lớp 1")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class1Mat.getMaterialId());

        mockMvc.perform(get("/api/v1/classes/" + classPhy101_01.getClassId() + "/materials")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[?(@.materialId == '" + sharedMat.getMaterialId() + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.materialId == '" + class1Mat.getMaterialId() + "')]").exists());
    }

    @Test
    @DisplayName("API-04: Sinh viên lớp A gọi danh sách học liệu của lớp B bị chặn 403 Forbidden")
    void testGetClassMaterials_byStudentNotInClass_status403() throws Exception {
        mockMvc.perform(get("/api/v1/classes/" + classPhy101_02.getClassId() + "/materials")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("không có trạng thái hoạt động")));
    }

    @Test
    @DisplayName("API-05: Sinh viên lớp B không thể xin download-url tệp CLASS của lớp A (403 Forbidden)")
    void testStudentClassB_cannotDownloadClassA_file_status403() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("class1_secret.pdf")
                .storedUrl("/api/v1/files/materials/class1_secret.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .status(FileProcessingStatus.COMPLETED)
                .fileSizeBytes(2048L)
                .build());
        createdUploadIds.add(upload.getFileId());

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 PDF Approved")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(upload.getStoredUrl())
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        // sv_dung học PHY101-02 -> xin link tải tệp lớp PHY101-01 -> 403 Forbidden
        mockMvc.perform(post("/api/v1/files/" + upload.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentDungToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Bạn không có quyền truy cập tệp học liệu này")));
    }

    @Test
    @DisplayName("API-06: Sinh viên lớp A xin download-url tệp CLASS của lớp A thành công 200 OK")
    void testStudentClassA_canDownloadClassA_file_status200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "valid_class1.pdf", "application/pdf", "%PDF-1.4 Valid".getBytes());
        String fileUrl = fileStorageService.storeFile(file, "materials");
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("valid_class1.pdf")
                .storedUrl(fileUrl)
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .status(FileProcessingStatus.COMPLETED)
                .fileSizeBytes(file.getSize())
                .build());
        createdUploadIds.add(upload.getFileId());

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Học liệu lớp 1 Approved")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(fileUrl)
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        mockMvc.perform(post("/api/v1/files/" + upload.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
    }

    @Test
    @DisplayName("API-07: Sinh viên không tải được tệp CLASS Material chưa APPROVED (403 Forbidden)")
    void testStudent_cannotDownloadPendingClassMaterialFile_status403() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("class1_pending.pdf")
                .storedUrl("/api/v1/files/materials/class1_pending.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .status(FileProcessingStatus.COMPLETED)
                .fileSizeBytes(1024L)
                .build());
        createdUploadIds.add(upload.getFileId());

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 PDF Pending")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(upload.getStoredUrl())
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        mockMvc.perform(post("/api/v1/files/" + upload.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("chưa được phê duyệt")));
    }

    @Test
    @DisplayName("API-08: Raw URL không thể bypass class-level authorization (403 Forbidden)")
    void testRawDownload_cannotBypassClassLevelAuthorization_status403() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("raw_class1_doc.pdf")
                .storedUrl("/api/v1/files/materials/raw_class1_doc.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .status(FileProcessingStatus.COMPLETED)
                .fileSizeBytes(1024L)
                .build());
        createdUploadIds.add(upload.getFileId());

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Raw Class 1 Doc")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(upload.getStoredUrl())
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        // sv_dung cố gọi trực tiếp raw URL của tài liệu lớp PHY101-01 -> 403
        mockMvc.perform(get("/api/v1/files/materials/raw_class1_doc.pdf")
                        .header("Authorization", "Bearer " + studentDungToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API-09: StudentPortal getMyMaterials không trả duplicate SHARED Material khi không truyền classId")
    void testStudentPortal_getMyMaterials_noDuplicates() throws Exception {
        LearningMaterial sharedMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Shared Unique In Portal")
                .type(MaterialType.MARKDOWN)
                .contentText("# Unique Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedMat.getMaterialId());

        String res = mockMvc.perform(get("/api/v1/students/me/materials")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Kiểm tra materialId của sharedMat chỉ xuất hiện đúng 1 lần
        int firstIndex = res.indexOf(sharedMat.getMaterialId().toString());
        int lastIndex = res.lastIndexOf(sharedMat.getMaterialId().toString());
        assertEquals(firstIndex, lastIndex, "Shared material must not be duplicated in portal response");
    }

    @Test
    @DisplayName("API-10: Public assets vẫn truy cập được không cần đăng nhập 200 OK")
    void testPublicAsset_remainsAccessible_status200() throws Exception {
        MockMultipartFile publicImage = new MockMultipartFile("file", "public_icon.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String uploadRes = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(publicImage)
                        .param("folder", "images")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(uploadRes);
        String fileUrl = root.get("data").get("url").asText();
        if (root.get("data").has("fileId")) {
            createdUploadIds.add(UUID.fromString(root.get("data").get("fileId").asText()));
        }

        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("API-11: Tạo học liệu Markdown dài qua JSON trực tiếp thành công 201 CREATED")
    void testMarkdownMaterial_createWithLongContentViaJson_success() throws Exception {
        StringBuilder longMarkdown = new StringBuilder("# Hướng dẫn thí nghiệm Vật lý 1\n\n");
        for (int i = 1; i <= 100; i++) {
            longMarkdown.append("### Phần ").append(i).append(": Nội dung lý thuyết và bài tập thực hành nâng cao\n");
            longMarkdown.append("Công thức: $$F = m \\cdot a$$, vận tốc $$v = \\frac{s}{t}$$.\n\n");
        }

        String jsonPayload = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Tài liệu Markdown Dài",
                "type", "MARKDOWN",
                "scope", "SHARED",
                "contentText", longMarkdown.toString()
        ));

        String res = mockMvc.perform(post("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title", is("Tài liệu Markdown Dài")))
                .andExpect(jsonPath("$.data.type", is("MARKDOWN")))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(res);
        UUID matId = UUID.fromString(node.get("data").get("materialId").asText());
        createdMaterialIds.add(matId);

        LearningMaterial saved = materialRepository.findById(matId).orElseThrow();
        assertEquals(longMarkdown.toString(), saved.getContentText());
    }

    @Test
    @DisplayName("API-12: Tải lên file README.md cho học liệu MARKDOWN tự động trích xuất nội dung và lưu trữ tệp")
    void testMarkdownMaterial_createWithUploadedReadmeFile_success() throws Exception {
        String readmeContent = "# README - Hướng dẫn nhập môn Vật lý 1\n\nChào mừng sinh viên đến với bộ môn.";
        MockMultipartFile readmeFile = new MockMultipartFile(
                "file", "README.md", "text/markdown", readmeContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String res = mockMvc.perform(multipart("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials")
                        .file(readmeFile)
                        .param("title", "README Nhập môn")
                        .param("type", "MARKDOWN")
                        .param("scope", "SHARED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title", is("README Nhập môn")))
                .andExpect(jsonPath("$.data.type", is("MARKDOWN")))
                .andExpect(jsonPath("$.data.contentText", containsString("README - Hướng dẫn nhập môn")))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(res);
        UUID matId = UUID.fromString(node.get("data").get("materialId").asText());
        createdMaterialIds.add(matId);

        LearningMaterial saved = materialRepository.findById(matId).orElseThrow();
        assertEquals(readmeContent, saved.getContentText());
        assertNotNull(saved.getFileUrl(), "File đính kèm của README.md phải được lưu trữ để sinh viên có thể tải về");
    }
}
