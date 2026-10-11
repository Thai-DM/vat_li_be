package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.request.CreateLearningMaterialDTO;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
public class ScopedLearningMaterialServiceTest {

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

    private User admin;
    private User gvNguyen;
    private User gvTran;
    private User svAn;
    private User svDung;

    private Subject phy101;
    private Subject phy102;
    private Topic topicPhy101;
    private Topic topicPhy102;
    private Class classPhy101_01;
    private Class classPhy101_02;

    private final List<UUID> createdMaterialIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        admin = userRepository.findByUsername("admin");
        gvNguyen = userRepository.findByUsername("gv_nguyen");
        gvTran = userRepository.findByUsername("gv_tran");
        svAn = userRepository.findByUsername("sv_an");
        svDung = userRepository.findByUsername("sv_dung");

        phy101 = subjectRepository.findAll().stream()
                .filter(s -> "PHY101".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        phy102 = subjectRepository.findAll().stream()
                .filter(s -> "PHY102".equals(s.getSubjectCode()))
                .findFirst().orElseThrow();

        topicPhy101 = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy101.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy101.getSubjectId())
                        .topicName("Scoped Topic PHY101")
                        .orderIndex(1)
                        .build()));

        topicPhy102 = topicRepository.findBySubjectIdOrderByOrderIndexAsc(phy102.getSubjectId())
                .stream().findFirst().orElseGet(() -> topicRepository.save(Topic.builder()
                        .subjectId(phy102.getSubjectId())
                        .topicName("Scoped Topic PHY102")
                        .orderIndex(1)
                        .build()));

        classPhy101_01 = classRepository.findByClassCode("PHY101-01").orElseThrow();
        classPhy101_02 = classRepository.findByClassCode("PHY101-02").orElseThrow();
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
    }

    private void authenticateAs(User user, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                "N/A",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. Instructor được phân công Subject tạo SHARED Material thành công (PENDING)")
    void testInstructorAssignedToSubject_createsSharedMaterial_success() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Tài liệu dùng chung PHY101")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung SHARED")
                .build();

        LearningMaterialDTO result = learningMaterialService.createMaterial(dto, gvNguyen.getUserId());

        assertNotNull(result.getMaterialId());
        createdMaterialIds.add(result.getMaterialId());
        assertEquals(MaterialScope.SHARED, result.getScope());
        assertNull(result.getClassId());
        assertEquals(phy101.getSubjectId(), result.getSubjectId());
        assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
    }

    @Test
    @DisplayName("2. Instructor chưa phân công Subject bị chặn tạo SHARED Material")
    void testInstructorNotAssignedToSubject_cannotCreateSharedMaterial_forbidden() {
        authenticateAs(gvTran, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .topicId(topicPhy102.getTopicId()) // gvTran không dạy PHY102
                .scope(MaterialScope.SHARED)
                .title("Tài liệu chéo môn")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung vi phạm")
                .build();

        assertThrows(CustomException.class, () -> {
            learningMaterialService.createMaterial(dto, gvTran.getUserId());
        });
    }

    @Test
    @DisplayName("3. Instructor tạo CLASS Material trong lớp phụ trách thành công (PENDING)")
    void testInstructorAssignedToClass_createsClassMaterial_success() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .topicId(topicPhy101.getTopicId())
                .classId(classPhy101_01.getClassId())
                .scope(MaterialScope.CLASS)
                .title("Tài liệu lớp PHY101-01")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung riêng lớp 1")
                .build();

        LearningMaterialDTO result = learningMaterialService.createClassMaterial(
                classPhy101_01.getClassId(),
                topicPhy101.getTopicId(),
                dto,
                gvNguyen.getUserId()
        );

        assertNotNull(result.getMaterialId());
        createdMaterialIds.add(result.getMaterialId());
        assertEquals(MaterialScope.CLASS, result.getScope());
        assertEquals(classPhy101_01.getClassId(), result.getClassId());
        assertEquals(phy101.getSubjectId(), result.getSubjectId());
        assertEquals(ApprovalStatus.PENDING, result.getApprovalStatus());
    }

    @Test
    @DisplayName("4. Instructor không phụ trách lớp bị chặn tạo CLASS Material ở lớp đó")
    void testInstructorNotAssignedToClass_cannotCreateClassMaterial_forbidden() {
        authenticateAs(gvTran, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("Tài liệu trái phép lớp khác")
                .type(MaterialType.MARKDOWN)
                .contentText("# Nội dung")
                .build();

        assertThrows(CustomException.class, () -> {
            learningMaterialService.createClassMaterial(
                    classPhy101_01.getClassId(),
                    topicPhy101.getTopicId(),
                    dto,
                    gvTran.getUserId()
            );
        });
    }

    @Test
    @DisplayName("5. Chặn tạo CLASS Material nếu Class và Topic khác Subject (400 Bad Request)")
    void testCreateClassMaterial_withMismatchedClassAndTopic_badRequest() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        CreateLearningMaterialDTO dto = CreateLearningMaterialDTO.builder()
                .title("Tài liệu lệch môn học")
                .type(MaterialType.MARKDOWN)
                .contentText("# Mismatched Subject")
                .build();

        assertThrows(CustomException.class, () -> {
            learningMaterialService.createClassMaterial(
                    classPhy101_01.getClassId(),
                    topicPhy102.getTopicId(),
                    dto,
                    gvNguyen.getUserId()
            );
        });
    }

    @Test
    @DisplayName("6. CLASS Material không thể bị chuyển thành SHARED qua update thông thường")
    void testClassMaterial_cannotChangeScopeViaUpdate_badRequest() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        LearningMaterial classMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class Material ban đầu")
                .type(MaterialType.MARKDOWN)
                .contentText("# Ban đầu")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(gvNguyen.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(classMat.getMaterialId());

        CreateLearningMaterialDTO updateDto = CreateLearningMaterialDTO.builder()
                .scope(MaterialScope.SHARED)
                .title("Đổi lén thành SHARED")
                .type(MaterialType.MARKDOWN)
                .contentText("# Bị cấm")
                .build();

        assertThrows(CustomException.class, () -> {
            learningMaterialService.updateMaterial(classMat.getMaterialId(), updateDto, gvNguyen.getUserId(), "INSTRUCTOR");
        });
    }

    @Test
    @DisplayName("7. CLASS Material không thể đổi classId sang lớp khác qua update thông thường")
    void testClassMaterial_cannotChangeClassIdViaUpdate_badRequest() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        LearningMaterial classMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class Material lớp 1")
                .type(MaterialType.MARKDOWN)
                .contentText("# Lớp 1")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(gvNguyen.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(classMat.getMaterialId());

        CreateLearningMaterialDTO updateDto = CreateLearningMaterialDTO.builder()
                .classId(classPhy101_02.getClassId())
                .title("Đổi lén classId")
                .type(MaterialType.MARKDOWN)
                .contentText("# Bị cấm")
                .build();

        assertThrows(CustomException.class, () -> {
            learningMaterialService.updateMaterial(classMat.getMaterialId(), updateDto, gvNguyen.getUserId(), "INSTRUCTOR");
        });
    }

    @Test
    @DisplayName("8. Instructor không thể tự phê duyệt (approveMaterial bị chặn)")
    void testInstructor_cannotSelfApproveMaterial_forbidden() {
        authenticateAs(gvNguyen, "INSTRUCTOR");

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Chờ duyệt")
                .type(MaterialType.MARKDOWN)
                .contentText("# Chờ")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(gvNguyen.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        assertThrows(CustomException.class, () -> {
            learningMaterialService.approveMaterial(mat.getMaterialId());
        });
    }

    @Test
    @DisplayName("9. Admin approve Material hợp lệ thành công")
    void testAdmin_canApproveMaterial_success() {
        authenticateAs(admin, "ADMIN");

        LearningMaterial mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Chờ Admin duyệt")
                .type(MaterialType.MARKDOWN)
                .contentText("# Chờ")
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(gvNguyen.getUserId())
                .version(1)
                .build());
        createdMaterialIds.add(mat.getMaterialId());

        LearningMaterialDTO approved = learningMaterialService.approveMaterial(mat.getMaterialId());
        assertEquals(ApprovalStatus.APPROVED, approved.getApprovalStatus());
    }

    @Test
    @DisplayName("10. Sinh viên lớp A xem danh sách học liệu theo lớp thấy SHARED + CLASS của lớp A, không thấy CLASS lớp B")
    void testGetMaterialsByClass_returnsSharedAndClassMaterials_forStudent() {
        LearningMaterial sharedApproved = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Shared Approved Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedApproved.getMaterialId());

        LearningMaterial class1Approved = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 Approved Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class 1")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class1Approved.getMaterialId());

        LearningMaterial class2Approved = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_02.getClassId())
                .title("Class 2 Approved Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class 2")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class2Approved.getMaterialId());

        authenticateAs(svAn, "STUDENT");

        List<LearningMaterialDTO> materials = learningMaterialService.getMaterialsByClass(
                classPhy101_01.getClassId(),
                null,
                null,
                "STUDENT"
        );

        assertTrue(materials.stream().anyMatch(m -> m.getMaterialId().equals(sharedApproved.getMaterialId())));
        assertTrue(materials.stream().anyMatch(m -> m.getMaterialId().equals(class1Approved.getMaterialId())));
        assertFalse(materials.stream().anyMatch(m -> m.getMaterialId().equals(class2Approved.getMaterialId())),
                "Student in Class 1 must NOT see Class 2 materials");
    }

    @Test
    @DisplayName("11. Sinh viên không có ACTIVE Enrollment trong Class bị chặn 403 khi gọi getMaterialsByClass")
    void testGetMaterialsByClass_studentNotEnrolled_forbidden() {
        authenticateAs(svAn, "STUDENT"); // svAn chỉ học PHY101-01, không học PHY101-02

        assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialsByClass(
                    classPhy101_02.getClassId(),
                    null,
                    null,
                    "STUDENT"
            );
        });
    }

    @Test
    @DisplayName("12. getMaterialsByTopic có classId trả SHARED + CLASS của lớp đó cho Sinh viên")
    void testGetMaterialsByTopic_studentWithClassId_returnsSharedAndClass() {
        LearningMaterial sharedMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Shared Topic Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedMat.getMaterialId());

        LearningMaterial class1Mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 Topic Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class 1")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class1Mat.getMaterialId());

        authenticateAs(svAn, "STUDENT");

        List<LearningMaterialDTO> list = learningMaterialService.getMaterialsByTopic(
                topicPhy101.getTopicId(),
                classPhy101_01.getClassId(),
                "STUDENT"
        );

        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(sharedMat.getMaterialId())));
        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(class1Mat.getMaterialId())));
    }

    @Test
    @DisplayName("13. getMaterialsByTopic không truyền classId chỉ trả SHARED Material cho Sinh viên")
    void testGetMaterialsByTopic_studentWithoutClassId_returnsOnlyShared() {
        LearningMaterial sharedMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.SHARED)
                .title("Shared Topic Mat Alone")
                .type(MaterialType.MARKDOWN)
                .contentText("# Shared")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(sharedMat.getMaterialId());

        LearningMaterial class1Mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 Topic Mat Excluded")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class 1")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class1Mat.getMaterialId());

        authenticateAs(svAn, "STUDENT");

        List<LearningMaterialDTO> list = learningMaterialService.getMaterialsByTopic(
                topicPhy101.getTopicId(),
                null,
                "STUDENT"
        );

        assertTrue(list.stream().anyMatch(m -> m.getMaterialId().equals(sharedMat.getMaterialId())));
        assertFalse(list.stream().anyMatch(m -> m.getMaterialId().equals(class1Mat.getMaterialId())),
                "When classId is omitted, CLASS materials must NOT be returned to student");
    }

    @Test
    @DisplayName("14. getMaterialById kiểm tra phạm vi CLASS của đúng lớp")
    void testGetMaterialById_classMaterial_enforcesClassEnrollment() {
        LearningMaterial class1Mat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 Secret Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Class 1 Secret")
                .approvalStatus(ApprovalStatus.APPROVED)
                .version(1)
                .build());
        createdMaterialIds.add(class1Mat.getMaterialId());

        authenticateAs(svDung, "STUDENT");

        // svDung không học PHY101-01 -> 403 Forbidden
        assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(class1Mat.getMaterialId(), "STUDENT");
        });

        // svAn học PHY101-01 -> xem được
        authenticateAs(svAn, "STUDENT");
        LearningMaterialDTO result = learningMaterialService.getMaterialById(class1Mat.getMaterialId(), "STUDENT");
        assertNotNull(result);
        assertEquals(class1Mat.getMaterialId(), result.getMaterialId());
    }

    @Test
    @DisplayName("15. Sinh viên không thể xem tài liệu PENDING qua getMaterialById")
    void testGetMaterialById_pendingMaterial_blockedForStudent() {
        LearningMaterial pendingMat = materialRepository.save(LearningMaterial.builder()
                .subjectId(phy101.getSubjectId())
                .topicId(topicPhy101.getTopicId())
                .scope(MaterialScope.CLASS)
                .classId(classPhy101_01.getClassId())
                .title("Class 1 Pending Mat")
                .type(MaterialType.MARKDOWN)
                .contentText("# Pending")
                .approvalStatus(ApprovalStatus.PENDING)
                .version(1)
                .build());
        createdMaterialIds.add(pendingMat.getMaterialId());

        authenticateAs(svAn, "STUDENT");

        assertThrows(CustomException.class, () -> {
            learningMaterialService.getMaterialById(pendingMat.getMaterialId(), "STUDENT");
        });
    }
}
