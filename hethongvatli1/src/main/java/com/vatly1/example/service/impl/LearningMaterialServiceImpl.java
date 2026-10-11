package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.CreateLearningMaterialDTO;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IFileStorageService;
import com.vatly1.example.service.ILearningMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningMaterialServiceImpl implements ILearningMaterialService {

    private final LearningMaterialRepository materialRepository;
    private final TopicRepository topicRepository;
    private final FileUploadRepository fileUploadRepository;
    private final IFileStorageService fileStorageService;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassRepository classRepository;
    private final IUserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userRepository.findByUsername(auth.getName());
        }
        return null;
    }

    private boolean checkIsAdmin(String role, Authentication auth) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }

    private boolean checkIsStudent(String role, Authentication auth) {
        if ("STUDENT".equalsIgnoreCase(role)) {
            return true;
        }
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()) || "STUDENT".equals(a.getAuthority()));
    }

    @Override
    public List<LearningMaterialDTO> getMaterialsByTopic(UUID topicId, String role) {
        return getMaterialsByTopic(topicId, null, role);
    }

    @Override
    public List<LearningMaterialDTO> getMaterialsByTopic(UUID topicId, UUID classId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);
        boolean isStudent = checkIsStudent(role, auth);

        User currentUser = getCurrentUser();
        UUID currentUserId = currentUser != null ? currentUser.getUserId() : null;

        List<LearningMaterial> materials;

        if (classId != null) {
            Class clazz = classRepository.findById(classId)
                    .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

            if (!Objects.equals(topic.getSubjectId(), clazz.getSubjectId())) {
                throw new CustomException("Chủ đề không thuộc môn học của lớp này", HttpStatus.BAD_REQUEST);
            }

            if (isStudent && !isAdmin) {
                if (currentUserId != null) {
                    boolean enrolled = enrollmentRepository.isStudentActivelyEnrolledInClass(classId, currentUserId);
                    if (!enrolled) {
                        throw new CustomException("Sinh viên không ghi danh hoặc không có trạng thái hoạt động trong lớp học này", HttpStatus.FORBIDDEN);
                    }
                }
            } else if (!isAdmin && currentUserId != null) {
                boolean isAssigned = classRepository.isInstructorAssignedToClass(classId, currentUserId)
                        || classRepository.isInstructorAssignedToSubject(clazz.getSubjectId(), currentUserId);
                if (!isAssigned) {
                    throw new CustomException("Bạn không có quyền quản lý lớp học này", HttpStatus.FORBIDDEN);
                }
            }

            // Lấy SHARED của topic + CLASS của classId và topicId
            List<LearningMaterial> sharedList = materialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(topicId, MaterialScope.SHARED);
            List<LearningMaterial> classList = materialRepository.findByClassIdAndTopicIdAndScopeOrderByCreatedAtDesc(classId, topicId, MaterialScope.CLASS);

            materials = new ArrayList<>(sharedList);
            materials.addAll(classList);
        } else {
            // Không truyền classId
            if (isStudent && !isAdmin) {
                if (currentUserId != null && topic.getSubjectId() != null) {
                    boolean enrolled = enrollmentRepository.isStudentActivelyEnrolledInSubject(currentUserId, topic.getSubjectId());
                    if (!enrolled) {
                        throw new CustomException("Bạn chưa ghi danh vào môn học này", HttpStatus.FORBIDDEN);
                    }
                }
                // Sinh viên không truyền classId chỉ được xem SHARED của Topic
                materials = materialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(topicId, MaterialScope.SHARED);
            } else if (!isAdmin && currentUserId != null) {
                // Giảng viên xem SHARED của topic
                materials = materialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(topicId, MaterialScope.SHARED);
            } else {
                // Admin thấy SHARED của topic
                materials = materialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(topicId, MaterialScope.SHARED);
            }
        }

        // Lọc trạng thái duyệt
        if (isStudent && !isAdmin) {
            materials = materials.stream()
                    .filter(m -> ApprovalStatus.APPROVED.equals(m.getApprovalStatus()))
                    .collect(Collectors.toList());
        } else if (!isAdmin && currentUserId != null) {
            materials = materials.stream()
                    .filter(m -> {
                        if (ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) || Objects.equals(m.getCreatedBy(), currentUserId)) {
                            return true;
                        }
                        if (m.getScope() == MaterialScope.CLASS && m.getClassId() != null) {
                            return classRepository.isInstructorAssignedToClass(m.getClassId(), currentUserId);
                        }
                        return topic.getSubjectId() != null && classRepository.isInstructorAssignedToSubject(topic.getSubjectId(), currentUserId);
                    })
                    .collect(Collectors.toList());
        }

        // Deduplicate và sắp xếp
        return materials.stream()
                .collect(Collectors.toMap(LearningMaterial::getMaterialId, m -> m, (m1, m2) -> m1, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(LearningMaterial::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<LearningMaterialDTO> getMaterialsByClass(UUID classId, UUID topicId, MaterialType type, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);
        boolean isStudent = checkIsStudent(role, auth);

        User currentUser = getCurrentUser();
        UUID currentUserId = currentUser != null ? currentUser.getUserId() : null;

        if (isStudent && !isAdmin) {
            if (currentUserId != null) {
                boolean enrolled = enrollmentRepository.isStudentActivelyEnrolledInClass(classId, currentUserId);
                if (!enrolled) {
                    throw new CustomException("Sinh viên không ghi danh hoặc không có trạng thái hoạt động trong lớp học này", HttpStatus.FORBIDDEN);
                }
            }
        } else if (!isAdmin && currentUserId != null) {
            boolean isAssigned = classRepository.isInstructorAssignedToClass(classId, currentUserId)
                    || classRepository.isInstructorAssignedToSubject(clazz.getSubjectId(), currentUserId);
            if (!isAssigned) {
                throw new CustomException("Bạn không có quyền quản lý lớp học này", HttpStatus.FORBIDDEN);
            }
        }

        if (topicId != null) {
            Topic topic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new CustomException("Chủ đề không tồn tại", HttpStatus.NOT_FOUND));
            if (!Objects.equals(topic.getSubjectId(), clazz.getSubjectId())) {
                throw new CustomException("Chủ đề không thuộc môn học của lớp này", HttpStatus.BAD_REQUEST);
            }
        }

        // 1. SHARED materials của Subject
        List<LearningMaterial> sharedMaterials;
        if (topicId != null) {
            sharedMaterials = materialRepository.findByTopicIdAndScopeOrderByCreatedAtDesc(topicId, MaterialScope.SHARED);
        } else {
            sharedMaterials = materialRepository.findBySubjectIdAndScopeOrderByCreatedAtDesc(clazz.getSubjectId(), MaterialScope.SHARED);
        }

        // 2. CLASS materials của chính classId
        List<LearningMaterial> classMaterials;
        if (topicId != null) {
            classMaterials = materialRepository.findByClassIdAndTopicIdAndScopeOrderByCreatedAtDesc(classId, topicId, MaterialScope.CLASS);
        } else {
            classMaterials = materialRepository.findByClassIdAndScopeOrderByCreatedAtDesc(classId, MaterialScope.CLASS);
        }

        List<LearningMaterial> combined = new ArrayList<>();
        combined.addAll(sharedMaterials);
        combined.addAll(classMaterials);

        // Lọc trạng thái duyệt & type
        List<LearningMaterial> filtered = combined.stream()
                .filter(m -> {
                    if (isStudent && !isAdmin) {
                        if (!ApprovalStatus.APPROVED.equals(m.getApprovalStatus())) {
                            return false;
                        }
                    } else if (!isAdmin && currentUserId != null) {
                        if (!ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) && !Objects.equals(m.getCreatedBy(), currentUserId)) {
                            if (m.getScope() == MaterialScope.CLASS && m.getClassId() != null) {
                                if (!classRepository.isInstructorAssignedToClass(m.getClassId(), currentUserId)) return false;
                            } else {
                                if (!classRepository.isInstructorAssignedToSubject(clazz.getSubjectId(), currentUserId)) return false;
                            }
                        }
                    }
                    if (type != null && m.getType() != type) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(LearningMaterial::getMaterialId, m -> m, (m1, m2) -> m1, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(LearningMaterial::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return filtered.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<LearningMaterialDTO> getAllMaterials(UUID topicId, String role) {
        if (topicId != null) {
            return getMaterialsByTopic(topicId, role);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);
        boolean isStudent = checkIsStudent(role, auth);

        User currentUser = getCurrentUser();
        UUID currentUserId = currentUser != null ? currentUser.getUserId() : null;

        if (isStudent && !isAdmin) {
            if (currentUserId == null) {
                return Collections.emptyList();
            }
            List<UUID> activeSubjectIds = enrollmentRepository.findActiveSubjectIdsByStudentId(currentUserId);
            if (activeSubjectIds.isEmpty()) {
                return Collections.emptyList();
            }
            List<Topic> topics = new ArrayList<>();
            for (UUID sid : activeSubjectIds) {
                topics.addAll(topicRepository.findBySubjectIdOrderByOrderIndexAsc(sid));
            }
            List<UUID> topicIds = topics.stream().map(Topic::getTopicId).collect(Collectors.toList());
            if (topicIds.isEmpty()) {
                return Collections.emptyList();
            }
            return materialRepository.findByTopicIdIn(topicIds).stream()
                    .filter(m -> ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) && m.getScope() == MaterialScope.SHARED)
                    .sorted(Comparator.comparing(LearningMaterial::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }

        List<LearningMaterial> materials = materialRepository.findAllByOrderByCreatedAtDesc();
        if (!isAdmin && currentUserId != null) {
            materials = materials.stream()
                    .filter(m -> {
                        if (ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) || Objects.equals(m.getCreatedBy(), currentUserId)) {
                            return true;
                        }
                        if (m.getScope() == MaterialScope.CLASS && m.getClassId() != null) {
                            return classRepository.isInstructorAssignedToClass(m.getClassId(), currentUserId);
                        }
                        Topic t = topicRepository.findById(m.getTopicId()).orElse(null);
                        return t != null && t.getSubjectId() != null && classRepository.isInstructorAssignedToSubject(t.getSubjectId(), currentUserId);
                    })
                    .collect(Collectors.toList());
        }

        return materials.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public LearningMaterialDTO getMaterialById(UUID materialId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));

        Topic topic = topicRepository.findById(material.getTopicId())
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);
        boolean isStudent = checkIsStudent(role, auth);

        User currentUser = getCurrentUser();
        UUID currentUserId = currentUser != null ? currentUser.getUserId() : null;

        if (isStudent && !isAdmin) {
            if (!ApprovalStatus.APPROVED.equals(material.getApprovalStatus())) {
                throw new CustomException("Material is pending approval", HttpStatus.FORBIDDEN);
            }
            if (material.getScope() == MaterialScope.CLASS) {
                if (material.getClassId() == null || currentUserId == null
                        || !enrollmentRepository.isStudentActivelyEnrolledInClass(material.getClassId(), currentUserId)) {
                    throw new CustomException("Bạn không có quyền truy cập học liệu của lớp này", HttpStatus.FORBIDDEN);
                }
            } else { // SHARED
                if (currentUserId != null && topic.getSubjectId() != null) {
                    boolean enrolled = enrollmentRepository.isStudentActivelyEnrolledInSubject(currentUserId, topic.getSubjectId());
                    if (!enrolled) {
                        throw new CustomException("Bạn chưa ghi danh vào môn học này", HttpStatus.FORBIDDEN);
                    }
                }
            }
        } else if (!isAdmin && currentUserId != null) {
            boolean isOwner = Objects.equals(material.getCreatedBy(), currentUserId);
            if (material.getScope() == MaterialScope.CLASS) {
                boolean isAssigned = material.getClassId() != null && classRepository.isInstructorAssignedToClass(material.getClassId(), currentUserId);
                if (!isOwner && !isAssigned) {
                    throw new CustomException("Bạn không có quyền truy cập học liệu của lớp này", HttpStatus.FORBIDDEN);
                }
            } else { // SHARED
                boolean isAssigned = topic.getSubjectId() != null && classRepository.isInstructorAssignedToSubject(topic.getSubjectId(), currentUserId);
                if (!isOwner && !isAssigned && !ApprovalStatus.APPROVED.equals(material.getApprovalStatus())) {
                    throw new CustomException("Bạn không có quyền truy cập học liệu này", HttpStatus.FORBIDDEN);
                }
            }
        }

        return mapToDTO(material);
    }

    @Override
    @Transactional
    public LearningMaterialDTO createMaterial(CreateLearningMaterialDTO dto, UUID currentUserId) {
        Topic topic = topicRepository.findById(dto.getTopicId())
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(null, auth);

        MaterialScope targetScope = dto.getScope() != null ? dto.getScope() : MaterialScope.SHARED;
        UUID targetClassId = dto.getClassId();

        if (targetScope == MaterialScope.SHARED) {
            targetClassId = null;
            if (!isAdmin && currentUserId != null) {
                boolean isAssigned = topic.getSubjectId() != null && classRepository.isInstructorAssignedToSubject(topic.getSubjectId(), currentUserId);
                if (!isAssigned) {
                    throw new CustomException("Giảng viên không được phân công giảng dạy môn học này", HttpStatus.FORBIDDEN);
                }
            }
        } else { // CLASS
            if (targetClassId == null) {
                throw new CustomException("Học liệu theo lớp (CLASS) bắt buộc phải có classId", HttpStatus.BAD_REQUEST);
            }
            Class clazz = classRepository.findById(targetClassId)
                    .orElseThrow(() -> new CustomException("Lớp học không tồn tại", HttpStatus.NOT_FOUND));

            if (!Objects.equals(clazz.getSubjectId(), topic.getSubjectId())) {
                throw new CustomException("Chủ đề và lớp học không cùng môn học", HttpStatus.BAD_REQUEST);
            }

            if (!isAdmin && currentUserId != null) {
                boolean isAssigned = classRepository.isInstructorAssignedToClass(targetClassId, currentUserId);
                if (!isAssigned) {
                    throw new CustomException("Giảng viên không phụ trách lớp học này", HttpStatus.FORBIDDEN);
                }
            }
        }

        if (dto.getType() == MaterialType.MARKDOWN) {
            if (dto.getContentText() == null || dto.getContentText().trim().isEmpty()) {
                throw new CustomException("Nội dung bài giảng Markdown không được để trống", HttpStatus.BAD_REQUEST);
            }
            if (dto.getFile() != null && !dto.getFile().isEmpty()) {
                throw new CustomException("Bài giảng dạng Markdown chỉ lưu nội dung văn bản trực tiếp trong cơ sở dữ liệu, không lưu dạng tệp đính kèm", HttpStatus.BAD_REQUEST);
            }
        }

        LearningMaterial material = LearningMaterial.builder()
                .topicId(dto.getTopicId())
                .subjectId(topic.getSubjectId())
                .scope(targetScope)
                .classId(targetClassId)
                .title(dto.getTitle())
                .type(dto.getType())
                .contentText(dto.getContentText())
                .sourceCitation(dto.getSourceCitation())
                .version(1)
                .approvalStatus(ApprovalStatus.PENDING) // Always PENDING initially
                .createdBy(currentUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Handle file upload
        if (dto.getType() != MaterialType.MARKDOWN && dto.getFile() != null && !dto.getFile().isEmpty()) {
            validateUploadedFile(dto.getFile(), dto.getType());

            String fileUrl = fileStorageService.storeFile(dto.getFile());
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(currentUserId)
                    .originalFilename(dto.getFile().getOriginalFilename())
                    .storedUrl(fileUrl)
                    .fileSizeBytes(dto.getFile().getSize())
                    .mimeType(dto.getFile().getContentType())
                    .purpose("LEARNING_MATERIAL")
                    .status(FileProcessingStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();

            fileUpload = fileUploadRepository.save(fileUpload);
            material.setFileId(fileUpload.getFileId());
            material.setFileUrl(fileUrl);
        }

        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public LearningMaterialDTO createClassMaterial(UUID classId, UUID topicId, CreateLearningMaterialDTO dto, UUID currentUserId) {
        dto.setClassId(classId);
        dto.setTopicId(topicId);
        dto.setScope(MaterialScope.CLASS);
        return createMaterial(dto, currentUserId);
    }

    @Override
    @Transactional
    public LearningMaterialDTO updateMaterial(UUID materialId, CreateLearningMaterialDTO dto, UUID currentUserId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);

        // Chống đổi scope hoặc đổi classId
        if (dto.getScope() != null && dto.getScope() != material.getScope()) {
            throw new CustomException("Không được phép thay đổi phạm vi học liệu", HttpStatus.BAD_REQUEST);
        }
        if (material.getScope() == MaterialScope.CLASS && dto.getClassId() != null && !dto.getClassId().equals(material.getClassId())) {
            throw new CustomException("Không được phép thay đổi lớp học của học liệu", HttpStatus.BAD_REQUEST);
        }

        if (!isAdmin) {
            if (!Objects.equals(material.getCreatedBy(), currentUserId)) {
                throw new CustomException("You do not have permission to update this material", HttpStatus.FORBIDDEN);
            }
            if (material.getScope() == MaterialScope.CLASS && material.getClassId() != null) {
                if (!classRepository.isInstructorAssignedToClass(material.getClassId(), currentUserId)) {
                    throw new CustomException("Giảng viên không phụ trách lớp học của tài liệu này", HttpStatus.FORBIDDEN);
                }
            } else if (material.getScope() == MaterialScope.SHARED && material.getSubjectId() != null) {
                if (!classRepository.isInstructorAssignedToSubject(material.getSubjectId(), currentUserId)) {
                    throw new CustomException("Giảng viên không phụ trách môn học của tài liệu này", HttpStatus.FORBIDDEN);
                }
            }
        }

        material.setTitle(dto.getTitle());
        material.setType(dto.getType());
        material.setContentText(dto.getContentText());
        material.setSourceCitation(dto.getSourceCitation());
        material.setVersion(material.getVersion() + 1);
        material.setUpdatedAt(Instant.now());
        material.setApprovalStatus(ApprovalStatus.PENDING); // Bắt buộc duyệt lại sau khi chỉnh sửa

        if (dto.getType() == MaterialType.MARKDOWN) {
            if (dto.getContentText() == null || dto.getContentText().trim().isEmpty()) {
                throw new CustomException("Nội dung bài giảng Markdown không được để trống", HttpStatus.BAD_REQUEST);
            }
            if (dto.getFile() != null && !dto.getFile().isEmpty()) {
                throw new CustomException("Bài giảng dạng Markdown chỉ lưu nội dung văn bản trực tiếp trong cơ sở dữ liệu, không lưu dạng tệp đính kèm", HttpStatus.BAD_REQUEST);
            }
            material.setFileId(null);
            material.setFileUrl(null);
        }

        if (dto.getType() != MaterialType.MARKDOWN && dto.getFile() != null && !dto.getFile().isEmpty()) {
            validateUploadedFile(dto.getFile(), dto.getType());

            String fileUrl = fileStorageService.storeFile(dto.getFile());
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(currentUserId)
                    .originalFilename(dto.getFile().getOriginalFilename())
                    .storedUrl(fileUrl)
                    .fileSizeBytes(dto.getFile().getSize())
                    .mimeType(dto.getFile().getContentType())
                    .purpose("LEARNING_MATERIAL")
                    .status(FileProcessingStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();

            fileUpload = fileUploadRepository.save(fileUpload);
            material.setFileId(fileUpload.getFileId());
            material.setFileUrl(fileUrl);
        }

        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    private void validateUploadedFile(org.springframework.web.multipart.MultipartFile file, MaterialType type) {
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new CustomException("Kích thước file vượt quá giới hạn 50MB", HttpStatus.BAD_REQUEST);
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".sh") || lower.endsWith(".cmd") || lower.endsWith(".vbs") || lower.endsWith(".msi")) {
                throw new CustomException("Định dạng file không được phép", HttpStatus.BAD_REQUEST);
            }
            if (type == MaterialType.PDF && !lower.endsWith(".pdf")) {
                throw new CustomException("File không đúng định dạng PDF", HttpStatus.BAD_REQUEST);
            }
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length >= 2 && bytes[0] == 0x4D && bytes[1] == 0x5A) {
                throw new CustomException("Nội dung tệp chứa chữ ký thực thi nguy hại (Windows MZ/PE binary)", HttpStatus.BAD_REQUEST);
            }
            if (bytes.length >= 4 && bytes[0] == 0x7F && bytes[1] == 0x45 && bytes[2] == 0x4C && bytes[3] == 0x46) {
                throw new CustomException("Nội dung tệp chứa chữ ký thực thi nguy hại (Linux ELF binary)", HttpStatus.BAD_REQUEST);
            }
        } catch (java.io.IOException e) {
            throw new CustomException("Không thể đọc nội dung tệp tin tải lên", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public LearningMaterialDTO approveMaterial(UUID materialId) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(null, auth);

        if (!isAdmin) {
            throw new CustomException("Chỉ Quản trị viên mới có quyền phê duyệt học liệu", HttpStatus.FORBIDDEN);
        }

        material.setApprovalStatus(ApprovalStatus.APPROVED);
        material.setUpdatedAt(Instant.now());

        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public void deleteMaterial(UUID materialId, UUID currentUserId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = checkIsAdmin(role, auth);

        if (!isAdmin) {
            if (!Objects.equals(material.getCreatedBy(), currentUserId)) {
                throw new CustomException("You do not have permission to delete this material", HttpStatus.FORBIDDEN);
            }
            if (material.getScope() == MaterialScope.CLASS && material.getClassId() != null) {
                if (!classRepository.isInstructorAssignedToClass(material.getClassId(), currentUserId)) {
                    throw new CustomException("You do not have permission to delete this material", HttpStatus.FORBIDDEN);
                }
            } else if (material.getScope() == MaterialScope.SHARED && material.getSubjectId() != null) {
                if (!classRepository.isInstructorAssignedToSubject(material.getSubjectId(), currentUserId)) {
                    throw new CustomException("You do not have permission to delete this material", HttpStatus.FORBIDDEN);
                }
            }
        }

        materialRepository.delete(material);
    }

    private LearningMaterialDTO mapToDTO(LearningMaterial material) {
        return LearningMaterialDTO.builder()
                .materialId(material.getMaterialId())
                .topicId(material.getTopicId())
                .subjectId(material.getSubjectId())
                .scope(material.getScope())
                .classId(material.getClassId())
                .fileId(material.getFileId())
                .title(material.getTitle())
                .type(material.getType())
                .fileUrl(material.getFileUrl())
                .contentText(material.getContentText())
                .version(material.getVersion())
                .approvalStatus(material.getApprovalStatus())
                .sourceCitation(material.getSourceCitation())
                .createdBy(material.getCreatedBy())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public int migrateLegacyTypes() {
        return materialRepository.migrateLegacyTypes();
    }
}