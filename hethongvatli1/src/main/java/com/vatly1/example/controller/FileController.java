package com.vatly1.example.controller;

import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.FileDownloadUrlDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.service.IFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.ClassStaffRole;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamAttemptRepository;
import com.vatly1.example.repository.IExamParticipantRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.IPracticeSessionQuestionRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File & Media Storage", description = "APIs Lưu trữ và Truy xuất tệp, tài liệu, hình ảnh qua MinIO Object Storage")
public class FileController {

    private final IFileStorageService fileStorageService;
    private final FileUploadRepository fileUploadRepository;
    private final LearningMaterialRepository materialRepository;
    private final TopicRepository topicRepository;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassRepository classRepository;
    private final IUserRepository userRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final IExamQuestionRepository examQuestionRepository;
    private final IExamRepository examRepository;
    private final IExamParticipantRepository examParticipantRepository;
    private final IExamAttemptRepository examAttemptRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IPracticeSessionQuestionRepository practiceSessionQuestionRepository;

    @Value("${security.jwt.token.secret-key:secret-key-fallback-for-signing-files}")
    private String hmacSecret;

    @Operation(summary = "Sinh URL tải tệp tạm thời", description = "Sinh URL tải file an toàn có thời hạn (15 phút), kiểm tra phân quyền truy cập.")
    @PostMapping("/{fileId}/download-url")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<FileDownloadUrlDTO>> generateDownloadUrl(
            @PathVariable UUID fileId,
            HttpServletRequest request) {
        String userIdStr = (String) request.getAttribute("userId");
        UUID currentUserId = null;
        if (userIdStr != null) {
            currentUserId = UUID.fromString(userIdStr);
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                User u = userRepository.findByUsername(auth.getName());
                if (u != null) {
                    currentUserId = u.getUserId();
                }
            }
        }
        String role = (String) request.getAttribute("role");
        if (role == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                role = auth.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .findFirst()
                        .orElse("STUDENT");
            }
        }

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new CustomException("File not found", HttpStatus.NOT_FOUND));

        verifyFileAccessPermission(fileUpload, currentUserId, role);

        Instant expiresAt = Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES);
        String dataToSign = fileId.toString() + ":" + expiresAt.toEpochMilli();
        String token = generateSignature(dataToSign);

        String downloadUrl = "/api/v1/files/" + fileId + "/download?token=" + token + "&expires=" + expiresAt.toEpochMilli();

        return ResponseEntity.ok(ApiResponse.<FileDownloadUrlDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Download URL generated successfully")
                .data(FileDownloadUrlDTO.builder()
                        .fileId(fileId)
                        .fileName(fileUpload.getOriginalFilename())
                        .mimeType(fileUpload.getMimeType())
                        .fileSizeBytes(fileUpload.getFileSizeBytes())
                        .downloadUrl(downloadUrl)
                        .expiresAt(expiresAt)
                        .build())
                .build());
    }

    @Operation(summary = "Tải tệp theo token tạm thời", description = "Tải tệp an toàn qua link tạm thời đã được ký số.")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFileByToken(
            @PathVariable UUID fileId,
            @RequestParam("token") String token,
            @RequestParam("expires") long expires) {

        if (Instant.now().toEpochMilli() > expires) {
            throw new CustomException("Download URL has expired", HttpStatus.FORBIDDEN);
        }

        String dataToSign = fileId.toString() + ":" + expires;
        if (!verifySignature(dataToSign, token)) {
            throw new CustomException("Invalid download token", HttpStatus.FORBIDDEN);
        }

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new CustomException("File not found", HttpStatus.NOT_FOUND));

        String storedUrl = fileUpload.getStoredUrl();
        String prefix = "/api/v1/files/";
        String filename = storedUrl != null && storedUrl.contains(prefix)
                ? storedUrl.substring(storedUrl.indexOf(prefix) + prefix.length())
                : (storedUrl != null ? storedUrl : fileUpload.getOriginalFilename());

        InputStream is = fileStorageService.getFileInputStream(filename);
        MediaType mediaType = fileUpload.getMimeType() != null
                ? MediaType.parseMediaType(fileUpload.getMimeType())
                : determineMediaType(fileUpload.getOriginalFilename());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileUpload.getOriginalFilename() + "\"")
                .body(new InputStreamResource(is));
    }

    private String generateSignature(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error signing download token", e);
        }
    }

    private boolean verifySignature(String data, String signature) {
        String expected = generateSignature(data);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    @Operation(summary = "Tải lên tệp hoặc hình ảnh (MinIO)", description = "Lưu trữ tệp, hình ảnh câu hỏi, minh chứng hoặc avatar vào MinIO bucket.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder) {
        String fileUrl = fileStorageService.storeFile(file, folder);
        boolean isMinio = fileStorageService.isMinioEnabled();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Map<String, Object>>builder()
                .status(HttpStatus.CREATED.value())
                .message("File uploaded successfully")
                .data(Map.of(
                        "url", fileUrl,
                        "storage", isMinio ? "MinIO Object Storage" : "Local Disk Fallback",
                        "size", file.getSize(),
                        "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream"
                ))
                .build());
    }

    private void verifyFileAccessPermission(FileUpload fileUpload, UUID currentUserId, String role) {
        verifyFileAccessPermission(fileUpload, currentUserId, role, fileUpload != null ? fileUpload.getStoredUrl() : null);
    }

    private void verifyFileAccessPermission(FileUpload fileUpload, UUID currentUserId, String role, String filename) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (isAdmin) {
            return;
        }

        if (fileUpload.getStatus() == com.vatly1.example.entity.enums.FileProcessingStatus.FAILED) {
            throw new CustomException("File is not available for download", HttpStatus.BAD_REQUEST);
        }

        if ("LEARNING_MATERIAL".equalsIgnoreCase(fileUpload.getPurpose())) {
            if (currentUserId == null) {
                throw new CustomException("Yêu cầu đăng nhập để truy cập tài liệu học tập", HttpStatus.UNAUTHORIZED);
            }

            List<LearningMaterial> materials = materialRepository.findByFileId(fileUpload.getFileId());
            if (materials.isEmpty() && fileUpload.getStoredUrl() != null) {
                materialRepository.findFirstByFileUrlContaining(fileUpload.getStoredUrl()).ifPresent(materials::add);
            }

            if (!materials.isEmpty()) {
                boolean isStudent = "STUDENT".equalsIgnoreCase(role);
                boolean hasAccess = false;

                for (LearningMaterial m : materials) {
                    Topic topic = topicRepository.findById(m.getTopicId()).orElse(null);
                    UUID subjectId = m.getSubjectId() != null ? m.getSubjectId() : (topic != null ? topic.getSubjectId() : null);

                    if (m.getScope() == com.vatly1.example.entity.enums.MaterialScope.CLASS) {
                        if (isStudent) {
                            if (ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) && m.getClassId() != null) {
                                if (enrollmentRepository.isStudentActivelyEnrolledInClass(m.getClassId(), currentUserId)) {
                                    hasAccess = true;
                                    break;
                                }
                            }
                        } else { // Instructor
                            boolean isOwner = Objects.equals(m.getCreatedBy(), currentUserId);
                            boolean isClassAssigned = m.getClassId() != null && classRepository.isInstructorAssignedToClass(m.getClassId(), currentUserId);
                            if (isOwner || isClassAssigned) {
                                hasAccess = true;
                                break;
                            }
                        }
                    } else { // SHARED
                        if (isStudent) {
                            if (ApprovalStatus.APPROVED.equals(m.getApprovalStatus()) && subjectId != null) {
                                if (enrollmentRepository.isStudentActivelyEnrolledInSubject(currentUserId, subjectId)) {
                                    hasAccess = true;
                                    break;
                                }
                            }
                        } else { // Instructor
                            boolean isOwner = Objects.equals(m.getCreatedBy(), currentUserId);
                            boolean isAssigned = subjectId != null && classRepository.isInstructorAssignedToSubject(subjectId, currentUserId);
                            if (isOwner || isAssigned || ApprovalStatus.APPROVED.equals(m.getApprovalStatus())) {
                                hasAccess = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasAccess) {
                    if (isStudent && materials.stream().noneMatch(m -> ApprovalStatus.APPROVED.equals(m.getApprovalStatus()))) {
                        throw new CustomException("Học liệu chưa được phê duyệt, không thể tải tệp", HttpStatus.FORBIDDEN);
                    }
                    throw new CustomException("Bạn không có quyền truy cập tệp học liệu này", HttpStatus.FORBIDDEN);
                }
            } else {
                if (!Objects.equals(fileUpload.getUploaderId(), currentUserId)) {
                    throw new CustomException("Bạn không có quyền truy cập tệp này", HttpStatus.FORBIDDEN);
                }
            }
        } else if ("EXPERIMENT_SUBMISSION".equalsIgnoreCase(fileUpload.getPurpose())) {
            boolean isOwner = Objects.equals(fileUpload.getUploaderId(), currentUserId);
            boolean isInstructor = "INSTRUCTOR".equalsIgnoreCase(role);
            if (!isOwner && !isInstructor) {
                throw new CustomException("Bạn không có quyền truy cập bài nộp thí nghiệm này", HttpStatus.FORBIDDEN);
            }
        } else if ("CHUNKED_UPLOAD".equalsIgnoreCase(fileUpload.getPurpose())) {
            boolean isOwner = Objects.equals(fileUpload.getUploaderId(), currentUserId);
            if (isOwner) {
                return;
            }

            // Trích xuất tên file định danh (ví dụ UUID.png) để tra cứu liên kết câu hỏi/đáp án
            String key = filename;
            if (key == null && fileUpload.getStoredUrl() != null) {
                key = fileUpload.getStoredUrl();
            }
            if (key != null && key.contains("/")) {
                key = key.substring(key.lastIndexOf('/') + 1);
            }

            List<QuestionBank> questions = new ArrayList<>();
            if (key != null && !key.isBlank()) {
                questions.addAll(questionBankRepository.findQuestionsReferencingFile(key));
                List<QuestionOption> options = questionOptionRepository.findOptionsReferencingFile(key);
                for (QuestionOption opt : options) {
                    if (opt.getQuestionId() != null) {
                        questionBankRepository.findById(opt.getQuestionId()).ifPresent(q -> {
                            if (questions.stream().noneMatch(existing -> existing.getQuestionId().equals(q.getQuestionId()))) {
                                questions.add(q);
                            }
                        });
                    }
                }
            }

            // Nếu không gắn với câu hỏi nào thì bắt buộc phải là người upload
            if (questions.isEmpty()) {
                throw new CustomException("Bạn không có quyền truy cập tệp tải lên này", HttpStatus.FORBIDDEN);
            }

            // Nếu người dùng hiện tại là người tạo câu hỏi
            if (questions.stream().anyMatch(q -> Objects.equals(q.getCreatedBy(), currentUserId))) {
                return;
            }

            boolean isInstructor = "INSTRUCTOR".equalsIgnoreCase(role);
            boolean isStudent = "STUDENT".equalsIgnoreCase(role);

            // Giảng viên: Được xem ảnh câu hỏi nếu câu hỏi thuộc ngân hàng câu hỏi (APPROVED), hoặc thuộc môn học phụ trách, hoặc trong đề thi của lớp phụ trách
            if (isInstructor) {
                boolean instructorHasAccess = false;
                for (QuestionBank q : questions) {
                    if (q.getApprovalStatus() == ApprovalStatus.APPROVED) {
                        instructorHasAccess = true;
                        break;
                    }
                    if (q.getSubjectId() != null && classRepository.isInstructorAssignedToSubject(q.getSubjectId(), currentUserId)) {
                        instructorHasAccess = true;
                        break;
                    }
                    List<UUID> examIds = examQuestionRepository.findDistinctExamIdsByQuestionIdIn(List.of(q.getQuestionId()));
                    for (UUID examId : examIds) {
                        Exam exam = examRepository.findById(examId).orElse(null);
                        if (exam != null && exam.getClassId() != null) {
                            Class clazz = classRepository.findById(exam.getClassId()).orElse(null);
                            if (clazz != null) {
                                boolean isPrimary = Objects.equals(clazz.getInstructorId(), currentUserId);
                                boolean isStaff = classStaffRepository.existsByClassIdAndUserIdAndRoleInClass(
                                        clazz.getClassId(), currentUserId, ClassStaffRole.INSTRUCTOR);
                                if (isPrimary || isStaff) {
                                    instructorHasAccess = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (instructorHasAccess) break;
                }

                if (instructorHasAccess) {
                    return;
                }
                throw new CustomException("Bạn không có quyền truy cập ảnh của câu hỏi này", HttpStatus.FORBIDDEN);
            }

            // Sinh viên: Được xem ảnh nếu đang làm/xem lại đề thi có chứa câu hỏi này (đã đến giờ thi hoặc đã làm), hoặc có trong phiên luyện tập, hoặc câu hỏi ngân hàng APPROVED thuộc môn học đang học
            if (isStudent) {
                boolean studentHasAccess = false;
                Instant now = Instant.now();

                for (QuestionBank q : questions) {
                    List<UUID> examIds = examQuestionRepository.findDistinctExamIdsByQuestionIdIn(List.of(q.getQuestionId()));
                    if (!examIds.isEmpty()) {
                        // Câu hỏi thuộc đề thi: chỉ sinh viên được phép làm bài (trong lớp của đề, hoặc là thí sinh, hoặc đã làm bài) mới được xem
                        for (UUID examId : examIds) {
                            Exam exam = examRepository.findById(examId).orElse(null);
                            if (exam != null) {
                                boolean isEnrolled = exam.getClassId() != null && enrollmentRepository.isStudentActivelyEnrolledInClass(exam.getClassId(), currentUserId);
                                boolean isParticipant = examParticipantRepository.existsByExamIdAndStudentId(examId, currentUserId);
                                boolean hasAttempt = examAttemptRepository.existsByExamIdAndStudentId(examId, currentUserId);

                                if (isEnrolled || isParticipant || hasAttempt) {
                                    if (exam.getStartTime() == null || !now.isBefore(exam.getStartTime()) || hasAttempt) {
                                        studentHasAccess = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // Nếu sinh viên có trong phiên luyện tập chứa câu hỏi này
                    if (!studentHasAccess && practiceSessionQuestionRepository.existsByStudentIdAndQuestionId(currentUserId, q.getQuestionId())) {
                        studentHasAccess = true;
                    }

                    // Nếu câu hỏi không gắn với đề thi nào và là câu hỏi APPROVED thuộc môn sinh viên đang học (tự ôn tập/ngân hàng câu hỏi)
                    if (!studentHasAccess && examIds.isEmpty() && q.getSubjectId() != null && q.getApprovalStatus() == ApprovalStatus.APPROVED) {
                        if (enrollmentRepository.isStudentActivelyEnrolledInSubject(currentUserId, q.getSubjectId())) {
                            studentHasAccess = true;
                        }
                    }

                    if (studentHasAccess) break;
                }

                if (studentHasAccess) {
                    return;
                }
                throw new CustomException("Bạn không có quyền truy cập hình ảnh trong bài thi/câu hỏi này", HttpStatus.FORBIDDEN);
            }

            throw new CustomException("Bạn không có quyền truy cập tệp tải lên này", HttpStatus.FORBIDDEN);
        }
    }

    @Operation(summary = "Truy xuất tệp hoặc hình ảnh", description = "Stream tệp/ảnh từ MinIO với đúng MediaType để hiển thị trực tiếp trên trình duyệt.")
    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> getFile(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/v1/files/";
        int index = fullPath.indexOf(prefix);
        String filename = (index != -1) ? fullPath.substring(index + prefix.length()) : fullPath;

        Optional<FileUpload> uploadOpt = fileUploadRepository.findFirstByStoredUrlContaining(filename);
        Optional<LearningMaterial> materialOpt = uploadOpt.isEmpty()
                ? materialRepository.findFirstByFileUrlContaining(filename)
                : Optional.empty();

        boolean isProtected = (uploadOpt.isPresent() && ("LEARNING_MATERIAL".equalsIgnoreCase(uploadOpt.get().getPurpose()) || "CHUNKED_UPLOAD".equalsIgnoreCase(uploadOpt.get().getPurpose())))
                || materialOpt.isPresent();

        if (isProtected) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                throw new CustomException("Yêu cầu đăng nhập để truy cập tài liệu hoặc tệp tải lên", HttpStatus.UNAUTHORIZED);
            }

            String role = (String) request.getAttribute("role");
            String userIdStr = (String) request.getAttribute("userId");
            UUID currentUserId = null;
            if (userIdStr != null) {
                currentUserId = UUID.fromString(userIdStr);
            } else {
                User u = userRepository.findByUsername(auth.getName());
                if (u != null) {
                    currentUserId = u.getUserId();
                }
            }

            if (role == null) {
                role = auth.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .findFirst()
                        .orElse("STUDENT");
            }

            if (uploadOpt.isPresent()) {
                verifyFileAccessPermission(uploadOpt.get(), currentUserId, role, filename);
            } else if (materialOpt.isPresent()) {
                FileUpload mockUpload = FileUpload.builder()
                        .fileId(materialOpt.get().getFileId())
                        .purpose("LEARNING_MATERIAL")
                        .storedUrl(materialOpt.get().getFileUrl())
                        .uploaderId(materialOpt.get().getCreatedBy())
                        .status(com.vatly1.example.entity.enums.FileProcessingStatus.COMPLETED)
                        .build();
                verifyFileAccessPermission(mockUpload, currentUserId, role, filename);
            }
        }

        InputStream is = fileStorageService.getFileInputStream(filename);
        MediaType mediaType = determineMediaType(filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, isProtected ? "no-cache, no-store, must-revalidate" : "public, max-age=86400")
                .body(new InputStreamResource(is));
    }

    private MediaType determineMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (lower.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (lower.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return MediaType.parseMediaType("text/markdown; charset=UTF-8");
        if (lower.endsWith(".txt")) return MediaType.parseMediaType("text/plain; charset=UTF-8");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}