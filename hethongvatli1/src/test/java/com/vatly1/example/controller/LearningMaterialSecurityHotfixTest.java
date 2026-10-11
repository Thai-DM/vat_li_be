package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.repository.*;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LearningMaterialSecurityHotfixTest {

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
    private com.vatly1.example.service.IFileStorageService fileStorageService;

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

        // Ensure topics exist for phy101 and phy102
        phy101Topic = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy101.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy101.getSubjectId())
                        .topicName("Security Test Topic PHY101")
                        .orderIndex(1)
                        .build()));

        phy102Topic = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy102.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy102.getSubjectId())
                        .topicName("Security Test Topic PHY102")
                        .orderIndex(1)
                        .build()));
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
    @DisplayName("SEC-01: Student lớp A truy cập tài liệu môn đang học thành công")
    void test01_student_accessEnrolledSubjectMaterials_success() throws Exception {
        // Tạo material APPROVED trong PHY101
        LearningMaterial material = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Tài liệu Vật lý 1 Approved")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung hợp lệ")
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());

        // sv_an gọi getMyMaterials với classId PHY101-01
        mockMvc.perform(get("/api/v1/students/me/materials")
                        .param("classId", classPhy101_01.getClassId().toString())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.materialId == '" + material.getMaterialId() + "')]").exists());

        // sv_an gọi API theo topic
        mockMvc.perform(get("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.materialId == '" + material.getMaterialId() + "')]").exists());
    }

    @Test
    @DisplayName("SEC-02: Student truyền classId của lớp không đăng ký bị chặn 403 Forbidden")
    void test02_student_passUnenrolledClassId_forbidden() throws Exception {
        // sv_an chỉ ghi danh lớp PHY101-01, KHÔNG ghi danh lớp PHY101-02
        mockMvc.perform(get("/api/v1/students/me/materials")
                        .param("classId", classPhy101_02.getClassId().toString())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("không có trạng thái hoạt động")));
    }

    @Test
    @DisplayName("SEC-03: Student không có Enrollment ACTIVE bị chặn 403 Forbidden")
    void test03_student_noActiveEnrollment_forbidden() throws Exception {
        // sv_dung chỉ ghi danh PHY101-02, truyền class PHY101-01 -> 403
        mockMvc.perform(get("/api/v1/students/me/materials")
                        .param("classId", classPhy101_01.getClassId().toString())
                        .header("Authorization", "Bearer " + studentDungToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-04: Topic khác Subject bị chặn (400 khi truyền classId, 403 khi không truyền classId)")
    void test04_topicOfDifferentSubject_blocked() throws Exception {
        // sv_an truyền classId của PHY101-01 nhưng topicId của PHY102 -> 400 Bad Request
        mockMvc.perform(get("/api/v1/students/me/materials")
                        .param("classId", classPhy101_01.getClassId().toString())
                        .param("topicId", phy102Topic.getTopicId().toString())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Chủ đề không thuộc môn học của lớp này")));

        // sv_an truyền topicId của môn chưa đăng ký mà không truyền classId -> 403 Forbidden
        mockMvc.perform(get("/api/v1/students/me/materials")
                        .param("topicId", phy102Topic.getTopicId().toString())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("không theo học môn học chứa chủ đề này")));
    }

    @Test
    @DisplayName("SEC-05: Sinh viên không xem được tài liệu PENDING")
    void test05_student_cannotViewPendingMaterial() throws Exception {
        LearningMaterial pendingMaterial = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Tài liệu Pending Bí Mật")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung chưa duyệt")
                .approvalStatus(ApprovalStatus.PENDING)
                .build());

        // Sinh viên truy cập chi tiết -> 403 Forbidden
        mockMvc.perform(get("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials/" + pendingMaterial.getMaterialId())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("pending approval")));

        // Trong danh sách topic materials không xuất hiện
        mockMvc.perform(get("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.materialId == '" + pendingMaterial.getMaterialId() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("SEC-06: Sinh viên không xem được tài liệu DRAFT")
    void test06_student_cannotViewDraftMaterial() throws Exception {
        LearningMaterial draftMaterial = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Tài liệu Bản Nháp")
                .type(MaterialType.MARKDOWN)
                .contentText("# Bản thảo")
                .approvalStatus(ApprovalStatus.DRAFT)
                .build());

        mockMvc.perform(get("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials/" + draftMaterial.getMaterialId())
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-07: Gọi download tài liệu không xác thực bị chặn 401 Unauthorized")
    void test07_unauthenticatedFileDownload_blocked() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("bai_tap_tuan_1.pdf")
                .storedUrl("/api/v1/files/materials/bai_tap_tuan_1.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .fileSizeBytes(1024L)
                .build());

        // Truy cập raw URL trực tiếp không có token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/files/materials/bai_tap_tuan_1.pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Yêu cầu đăng nhập để truy cập tài liệu học tập")));
    }

    @Test
    @DisplayName("SEC-08: User đã đăng nhập nhưng không có quyền môn học vẫn không tải được file 403 Forbidden")
    void test08_authenticatedUserWithoutPermission_cannotDownloadFile() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload uploadPhy102 = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("phy102_doc.pdf")
                .storedUrl("/api/v1/files/materials/phy102_doc.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .fileSizeBytes(2048L)
                .build());

        materialRepository.save(LearningMaterial.builder()
                .subjectId(phy102.getSubjectId())
                .topicId(phy102Topic.getTopicId())
                .title("Tài liệu PHY102 Approved")
                .type(MaterialType.PDF)
                .fileId(uploadPhy102.getFileId())
                .fileUrl(uploadPhy102.getStoredUrl())
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());

        // sv_an không học PHY102 -> xin download URL bị 403
        mockMvc.perform(post("/api/v1/files/" + uploadPhy102.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Bạn không có quyền truy cập tệp học liệu này")));

        // sv_an gọi raw URL cũng bị 403
        mockMvc.perform(get("/api/v1/files/materials/phy102_doc.pdf")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-09: User có quyền được tạo URL tải và tải file hợp lệ 200 OK")
    void test09_authorizedUser_canDownloadValidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "valid_phy101.pdf", "application/pdf", "%PDF-1.4 Content".getBytes());
        String fileUrl = fileStorageService.storeFile(file, "materials");
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("valid_phy101.pdf")
                .storedUrl(fileUrl)
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .fileSizeBytes(file.getSize())
                .build());

        // Gắn vào material APPROVED của môn PHY101
        materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Học liệu hợp lệ PHY101")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(fileUrl)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build());

        // sv_an xin download URL -> 200 OK
        String downloadUrlRes = mockMvc.perform(post("/api/v1/files/" + upload.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String dlUrl = objectMapper.readTree(downloadUrlRes).get("data").get("downloadUrl").asText();

        // Tải qua download token -> 200 OK
        mockMvc.perform(get(dlUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // sv_an stream trực tiếp với token -> 200 OK
        mockMvc.perform(get(fileUrl)
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("SEC-10: Không thể bypass bằng signed URL giả mạo hoặc hết hạn -> 403 Forbidden")
    void test10_bypassWithForgedOrExpiredSignedUrl_blocked() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("test_bypass.pdf")
                .storedUrl("/api/v1/files/materials/test_bypass.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .fileSizeBytes(1024L)
                .build());

        // Token giả
        mockMvc.perform(get("/api/v1/files/" + upload.getFileId() + "/download?token=fake_mac&expires=" + (System.currentTimeMillis() + 60000)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Invalid download token")));

        // Hết hạn
        mockMvc.perform(get("/api/v1/files/" + upload.getFileId() + "/download?token=fake_mac&expires=" + (System.currentTimeMillis() - 60000)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("expired")));
    }

    @Test
    @DisplayName("SEC-11: Instructor tự approve SHARED Material bị chặn 403 Forbidden")
    void test11_instructor_selfApproveSharedMaterial_forbidden() throws Exception {
        LearningMaterial pendingMaterial = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Tài liệu cần duyệt")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung")
                .approvalStatus(ApprovalStatus.PENDING)
                .build());

        // Giảng viên gọi approve -> 403 Forbidden
        mockMvc.perform(put("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials/" + pendingMaterial.getMaterialId() + "/approve")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-12: Admin approve Material hợp lệ 200 OK")
    void test12_admin_approveMaterial_success() throws Exception {
        LearningMaterial pendingMaterial = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Tài liệu chờ Admin duyệt")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung chuẩn")
                .approvalStatus(ApprovalStatus.PENDING)
                .build());

        mockMvc.perform(put("/api/v1/topics/" + phy101Topic.getTopicId() + "/materials/" + pendingMaterial.getMaterialId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        LearningMaterial updated = materialRepository.findById(pendingMaterial.getMaterialId()).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, updated.getApprovalStatus());
    }

    @Test
    @DisplayName("SEC-13: Instructor không thể phê duyệt tài liệu của Subject khác")
    void test13_instructor_cannotApproveOtherSubjectMaterial() throws Exception {
        LearningMaterial pendingMaterial = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy102.getSubjectId())
                .topicId(phy102Topic.getTopicId())
                .title("Tài liệu môn khác")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung môn khác")
                .approvalStatus(ApprovalStatus.PENDING)
                .build());

        mockMvc.perform(put("/api/v1/topics/" + phy102Topic.getTopicId() + "/materials/" + pendingMaterial.getMaterialId() + "/approve")
                        .header("Authorization", "Bearer " + otherInstructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-14: Các endpoint public asset hợp lệ vẫn hoạt động theo chính sách đã xác minh 200 OK")
    void test14_publicAssets_remainAccessible() throws Exception {
        MockMultipartFile publicImage = new MockMultipartFile("file", "public_diagram.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String uploadRes = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(publicImage)
                        .param("folder", "images")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String fileUrl = objectMapper.readTree(uploadRes).get("data").get("url").asText();

        // Không gửi Authorization header -> vẫn truy xuất được 200 OK
        mockMvc.perform(get(fileUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"));
    }

    @Test
    @DisplayName("SEC-15: Sinh viên cố tải tệp của học liệu PENDING bị chặn 403 Forbidden")
    void test15_student_downloadPendingMaterialFile_blocked() throws Exception {
        User admin = userRepository.findByUsername("admin");
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("pending_exam_prep.pdf")
                .storedUrl("/api/v1/files/materials/pending_exam_prep.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .fileSizeBytes(1024L)
                .build());

        materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(phy101Topic.getTopicId())
                .title("Đề cương chưa duyệt")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(upload.getStoredUrl())
                .approvalStatus(ApprovalStatus.PENDING)
                .build());

        // Sinh viên sv_an xin download URL của tệp PENDING -> 403 Forbidden
        mockMvc.perform(post("/api/v1/files/" + upload.getFileId() + "/download-url")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("chưa được phê duyệt")));

        // Sinh viên sv_an gọi raw URL -> 403 Forbidden
        mockMvc.perform(get("/api/v1/files/materials/pending_exam_prep.pdf")
                        .header("Authorization", "Bearer " + studentAnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("chưa được phê duyệt")));
    }
}
