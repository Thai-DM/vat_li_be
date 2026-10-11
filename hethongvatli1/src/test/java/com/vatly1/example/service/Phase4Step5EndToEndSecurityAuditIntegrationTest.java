package com.vatly1.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.request.CreateLearningMaterialDTO;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
public class Phase4Step5EndToEndSecurityAuditIntegrationTest {

    @Autowired
    private ILearningMaterialService learningMaterialService;

    @Autowired
    private LearningMaterialRepository materialRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassEnrollmentRepository enrollmentRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    private User admin;
    private User instructorA;
    private User instructorB;
    private User studentA;
    private User studentB;
    private User studentC;

    private Subject subjectS;
    private Subject otherSubject;
    private Topic topicS;
    private Topic otherTopic;
    private Class classA;
    private Class classB;

    private ClassEnrollment enrollmentStudentB;

    private final List<UUID> createdMaterialIds = new ArrayList<>();
    private final List<UUID> createdUploadIds = new ArrayList<>();
    private final List<UUID> createdEnrollmentIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        admin = userRepository.findByUsername("admin");
        instructorA = userRepository.findByUsername("gv_nguyen");
        instructorB = userRepository.findByUsername("gv_tran");
        studentA = userRepository.findByUsername("sv_an");
        studentB = userRepository.findByUsername("sv_dung");

        // Tạo tài khoản sinh viên C độc lập hoàn toàn không có enrollment nào
        studentC = userRepository.save(User.builder()
                .username("sv_audit_c_" + System.currentTimeMillis())
                .email("sv_audit_c_" + System.currentTimeMillis() + "@audit.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(studentC.getUserId());

        subjectS = subjectRepository.findAll().stream()
                .filter(s -> "PHY101".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        otherSubject = subjectRepository.findAll().stream()
                .filter(s -> "PHY102".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        classA = classRepository.findByClassCode("PHY101-01").orElseThrow();
        classB = classRepository.findByClassCode("PHY101-02").orElseThrow();

        topicS = topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectS.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(subjectS.getSubjectId())
                        .topicName("E2E Topic S")
                        .orderIndex(1)
                        .build()));

        otherTopic = topicRepository.findBySubjectIdOrderByOrderIndexAsc(otherSubject.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(otherSubject.getSubjectId())
                        .topicName("E2E Other Topic")
                        .orderIndex(1)
                        .build()));

        // Set up active enrollment for studentB in classB for isolated testing
        enrollmentStudentB = enrollmentRepository.save(ClassEnrollment.builder()
                .classId(classB.getClassId())
                .studentId(studentB.getUserId())
                .subjectId(classB.getSubjectId())
                .semesterId(classB.getSemesterId())
                .status(EnrollmentStatus.ACTIVE)
                .build());
        createdEnrollmentIds.add(enrollmentStudentB.getEnrollmentId());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

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

        for (UUID id : createdEnrollmentIds) {
            try {
                enrollmentRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdEnrollmentIds.clear();

        for (UUID id : createdUserIds) {
            try {
                userRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
    }

    private void authenticateAs(User user, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==========================================
    // SECTION 3: END-TO-END BUSINESS WORKFLOW (A-L)
    // ==========================================

    @Test
    @DisplayName("E2E-A: Instructor A tạo SHARED Material thành công ở trạng thái PENDING")
    void testE2E_A_InstructorA_CreatesSharedMaterial_Pending() {
        authenticateAs(instructorA, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .topicId(topicS.getTopicId())
                .title("E2E Shared Material")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared Theory Content")
                .build();

        LearningMaterialDTO result = learningMaterialService.createMaterial(dto, instructorA.getUserId());
        assertNotNull(result);
        createdMaterialIds.add(result.getMaterialId());

        assertEquals(MaterialScope.SHARED, result.getScope());
        assertNull(result.getClassId());
        assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
        assertEquals(subjectS.getSubjectId(), result.getSubjectId());
    }

    @Test
    @DisplayName("E2E-B: Instructor A tạo CLASS A Material thành công ở trạng thái PENDING")
    void testE2E_B_InstructorA_CreatesClassAMaterial_Pending() {
        authenticateAs(instructorA, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("E2E Class A Exercise")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class A Specific Exercise")
                .build();

        LearningMaterialDTO result = learningMaterialService.createClassMaterial(
                classA.getClassId(), topicS.getTopicId(), dto, instructorA.getUserId());
        assertNotNull(result);
        createdMaterialIds.add(result.getMaterialId());

        assertEquals(MaterialScope.CLASS, result.getScope());
        assertEquals(classA.getClassId(), result.getClassId());
        assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
        assertEquals(subjectS.getSubjectId(), result.getSubjectId());
    }

    @Test
    @DisplayName("E2E-C: Instructor A KHÔNG ĐƯỢC tự approve học liệu của mình")
    void testE2E_C_InstructorA_CannotSelfApprove() {
        authenticateAs(instructorA, "INSTRUCTOR");

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Pending Class Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Pending")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructorA.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.approveMaterial(mat.getMaterialId());
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("chỉ dành cho quản trị viên") || ex.getMessage().contains("Admin") || ex.getMessage().contains("Quản trị viên"));
    }

    @Test
    @DisplayName("E2E-D: Admin approve hợp lệ học liệu SHARED và CLASS A")
    void testE2E_D_Admin_ApprovesMaterials() {
        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Pending For Admin Approval")
                .type(MaterialType.MARKDOWN)
                .contentText("# Pending Content")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructorA.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        authenticateAs(admin, "ADMIN");
        LearningMaterialDTO approved = learningMaterialService.approveMaterial(mat.getMaterialId());
        assertNotNull(approved);
        assertEquals(ApprovalStatus.APPROVED, approved.getApprovalStatus());
    }

    @Test
    @DisplayName("E2E-E: Student A thấy SHARED và CLASS A (chỉ APPROVED)")
    void testE2E_E_StudentA_SeesSharedAndClassA() {
        LearningMaterial sharedApproved = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("E2E Shared Approved")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedApproved.getMaterialId());

        LearningMaterial classAApproved = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("E2E Class A Approved")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class A")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classAApproved.getMaterialId());

        authenticateAs(studentA, "STUDENT");
        List<LearningMaterialDTO> list = learningMaterialService.getMaterialsByClass(
                classA.getClassId(), null, null, "STUDENT");

        assertNotNull(list);
        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(sharedApproved.getMaterialId())));
        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(classAApproved.getMaterialId())));
    }

    @Test
    @DisplayName("E2E-F: Student B thấy SHARED và CLASS B, TUYỆT ĐỐI KHÔNG thấy CLASS A")
    void testE2E_F_StudentB_SeesSharedAndClassB_NotClassA() {
        LearningMaterial sharedMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("E2E Shared For All Classes")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedMat.getMaterialId());

        LearningMaterial classAMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("E2E Class A Secret")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class A Only")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classAMat.getMaterialId());

        LearningMaterial classBMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classB.getClassId())
                .title("E2E Class B Content")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class B Only")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classBMat.getMaterialId());

        authenticateAs(studentB, "STUDENT");
        List<LearningMaterialDTO> list = learningMaterialService.getMaterialsByClass(
                classB.getClassId(), null, null, "STUDENT");

        assertNotNull(list);
        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(sharedMat.getMaterialId())));
        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(classBMat.getMaterialId())));
        assertFalse(list.stream().anyMatch(m -> m.getMaterialId().equals(classAMat.getMaterialId())),
                "Student B must NEVER see Class A material");
    }

    @Test
    @DisplayName("E2E-G: Student C không có active enrollment bị từ chối truy cập (403 Forbidden)")
    void testE2E_G_StudentC_NoActiveEnrollment_Blocked() {
        authenticateAs(studentC, "STUDENT");

        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialsByClass(classA.getClassId(), null, null, "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("E2E-H & I: Student A tải file CLASS A thành công, Student B bị chặn khi cố tải file CLASS A")
    void testE2E_H_and_I_FileAccessIsolation_BetweenClasses() {
        FileUpload upload = fileUploadRepository.save(FileUpload.builder()
                .uploaderId(admin.getUserId())
                .originalFilename("class_a_exam_hints.pdf")
                .storedUrl("/api/v1/files/materials/class_a_exam_hints.pdf")
                .mimeType("application/pdf")
                .purpose("LEARNING_MATERIAL")
                .status(FileProcessingStatus.COMPLETED)
                .fileSizeBytes(1024L)
                .build());
        createdUploadIds.add(upload.getFileId());

        LearningMaterial classAMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class A Exam Hints")
                .type(MaterialType.PDF)
                .fileId(upload.getFileId())
                .fileUrl(upload.getStoredUrl())
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classAMat.getMaterialId());

        // Student A thuộc Class A -> xem getMaterialById thành công
        authenticateAs(studentA, "STUDENT");
        LearningMaterialDTO studentAResult = learningMaterialService.getMaterialById(classAMat.getMaterialId(), "STUDENT");
        assertNotNull(studentAResult);
        assertEquals(upload.getFileId(), studentAResult.getFileId());

        // Student B thuộc Class B -> cố truy cập getMaterialById của Class A -> 403 Forbidden
        authenticateAs(studentB, "STUDENT");
        CustomException exB = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(classAMat.getMaterialId(), "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, exB.getHttpStatus());
        assertTrue(exB.getMessage().contains("không có quyền truy cập học liệu của lớp này"));
    }

    @Test
    @DisplayName("E2E-J: Sinh viên KHÔNG THỂ xem hoặc tải tài liệu PENDING")
    void testE2E_J_Student_CannotViewPendingMaterial() {
        LearningMaterial pendingMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class A Pending File")
                .type(MaterialType.MARKDOWN)
                .contentText("# Hidden Pending")
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .build());
        createdMaterialIds.add(pendingMat.getMaterialId());

        authenticateAs(studentA, "STUDENT");
        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(pendingMat.getMaterialId(), "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("pending approval") || ex.getMessage().contains("chưa được phê duyệt"));
    }

    @Test
    @DisplayName("E2E-K: Khi Enrollment của Student chuyển sang DROPPED -> yêu cầu mới bị chặn ngay lập tức")
    void testE2E_K_EnrollmentDropped_RevokesAccessImmediately() {
        LearningMaterial classBMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classB.getClassId())
                .title("Class B Active Content")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class B Content")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classBMat.getMaterialId());

        authenticateAs(studentB, "STUDENT");

        // Trước khi drop: xem thành công
        LearningMaterialDTO resBefore = learningMaterialService.getMaterialById(classBMat.getMaterialId(), "STUDENT");
        assertNotNull(resBefore);

        // Chuyển enrollment sang DROPPED
        enrollmentStudentB.setStatus(EnrollmentStatus.DROPPED);
        enrollmentRepository.save(enrollmentStudentB);

        // Sau khi drop: yêu cầu mới lập tức bị chặn 403
        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(classBMat.getMaterialId(), "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("E2E-L: Khi Material chuyển sang REJECTED -> yêu cầu truy cập mới của Student bị từ chối")
    void testE2E_L_MaterialRevertedFromApproved_BlocksStudentAccess() {
        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Initially Approved Material")
                .type(MaterialType.MARKDOWN)
                .contentText("# Content")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        authenticateAs(studentA, "STUDENT");
        assertNotNull(learningMaterialService.getMaterialById(mat.getMaterialId(), "STUDENT"));

        // Admin thu hồi / reject tài liệu
        mat.setApprovalStatus(ApprovalStatus.REJECTED);
        materialRepository.save(mat);

        // Student A cố truy cập lại -> 403 Forbidden
        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(mat.getMaterialId(), "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    // ==========================================
    // SECTION 4: IDOR SECURITY AUDIT (CASES 1-10)
    // ==========================================

    @Test
    @DisplayName("IDOR-01 & 02: Student A biết ID lớp khác hoặc materialId của lớp khác -> Bị chặn 403")
    void testIDOR_CrossClassAccess_Blocked() {
        LearningMaterial classBMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classB.getClassId())
                .title("Class B Secret Test")
                .type(MaterialType.MARKDOWN)
                .contentText("# Secret")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(classBMat.getMaterialId());

        authenticateAs(studentA, "STUDENT");

        // 1. Thử lấy danh sách tài liệu lớp B
        CustomException exClassList = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialsByClass(classB.getClassId(), null, null, "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, exClassList.getHttpStatus());

        // 2. Thử truy cập trực tiếp ID của material lớp B
        CustomException exDirectMat = assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(classBMat.getMaterialId(), "STUDENT");
        });
        assertEquals(HttpStatus.FORBIDDEN, exDirectMat.getHttpStatus());
    }

    @Test
    @DisplayName("IDOR-03: Topic và Class khác Subject khi tạo CLASS Material -> Bị chặn 400 Bad Request")
    void testIDOR_MismatchedTopicAndClassSubject_ThrowsBadRequest() {
        authenticateAs(instructorA, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("Mismatched Material")
                .type(MaterialType.MARKDOWN)
                .contentText("# Content")
                .build();

        // classA thuộc PHY101, otherTopic thuộc PHY102
        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.createClassMaterial(classA.getClassId(), otherTopic.getTopicId(), dto, instructorA.getUserId());
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("không cùng môn học") || ex.getMessage().contains("cùng một môn học"));
    }

    @Test
    @DisplayName("IDOR-05: Giảng viên phụ trách lớp B cố tạo học liệu trong lớp A -> Bị chặn 403 Forbidden")
    void testIDOR_InstructorB_CreateMaterialInClassA_Forbidden() {
        authenticateAs(instructorB, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("Unauthorized Material In Class A")
                .type(MaterialType.MARKDOWN)
                .contentText("# Hack")
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.createClassMaterial(classA.getClassId(), topicS.getTopicId(), dto, instructorB.getUserId());
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("phụ trách lớp"));
    }

    @Test
    @DisplayName("IDOR-06: Giảng viên lớp B cố sửa học liệu CLASS của lớp A -> Bị chặn 403 Forbidden")
    void testIDOR_InstructorB_UpdateClassAMaterial_Forbidden() {
        LearningMaterial classAMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class A Original")
                .type(MaterialType.MARKDOWN)
                .contentText("# Original")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(instructorA.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(classAMat.getMaterialId());

        authenticateAs(instructorB, "INSTRUCTOR");

        CreateLearningMaterialDTO updateDTO = CreateLearningMaterialDTO.builder()
                .title("Hacked Title by Instructor B")
                .type(MaterialType.MARKDOWN)
                .contentText("# Modified")
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.updateMaterial(classAMat.getMaterialId(), updateDTO, instructorB.getUserId(), "INSTRUCTOR");
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("IDOR-10: Cố tình giả mạo scope và classId qua Update DTO -> Bị từ chối hoặc giữ nguyên tính bất biến")
    void testIDOR_TamperScopeAndClass_Blocked() {
        LearningMaterial classAMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classA.getClassId())
                .title("Class A Protected Scope")
                .type(MaterialType.MARKDOWN)
                .contentText("# Original")
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(instructorA.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(classAMat.getMaterialId());

        authenticateAs(instructorA, "INSTRUCTOR");

        // Cố tình chuyển sang SHARED
        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("Updated Title")
                .type(MaterialType.MARKDOWN)
                .contentText("# Updated Content")
                .scope(MaterialScope.SHARED)
                .classId(null)
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> {
            learningMaterialService.updateMaterial(classAMat.getMaterialId(), dto, instructorA.getUserId(), "INSTRUCTOR");
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Không được phép thay đổi phạm vi"));
    }

    // ==========================================
    // SECTION 7: CONCURRENCY & TRANSACTION SAFETY
    // ==========================================

    @Test
    @DisplayName("TRANSACTION-01: Update đồng thời - Kiểm tra tính toàn vẹn phiên bản (Version Integrity)")
    void testConcurrency_OptimisticLocking_VersionIntegrity() {
        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(subjectS.getSubjectId())
                .topicId(topicS.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Version Integrity Test")
                .type(MaterialType.MARKDOWN)
                .contentText("# V1")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        authenticateAs(admin, "ADMIN");

        CreateLearningMaterialDTO update1 = CreateLearningMaterialDTO.builder()
                .title("Title Updated First")
                .type(MaterialType.MARKDOWN)
                .contentText("# V1 Updated")
                .build();
        LearningMaterialDTO res1 = learningMaterialService.updateMaterial(mat.getMaterialId(), update1, admin.getUserId(), "ADMIN");
        assertNotNull(res1);

        LearningMaterial current = materialRepository.findById(mat.getMaterialId()).orElseThrow();
        assertEquals("Title Updated First", current.getTitle());
        assertNotNull(current.getVersion());
    }
}
