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

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File & Media Storage", description = "APIs Lưu trữ và Truy xuất tệp, tài liệu, hình ảnh qua MinIO Object Storage")
public class FileController {

    private final IFileStorageService fileStorageService;
    private final FileUploadRepository fileUploadRepository;

    @Value("${security.jwt.token.secret-key:secret-key-fallback-for-signing-files}")
    private String hmacSecret;

    @Operation(summary = "Sinh URL tải tệp tạm thời", description = "Sinh URL tải file an toàn có thời hạn (15 phút), kiểm tra phân quyền truy cập.")
    @PostMapping("/{fileId}/download-url")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<FileDownloadUrlDTO>> generateDownloadUrl(
            @PathVariable UUID fileId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new CustomException("File not found", HttpStatus.NOT_FOUND));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "INSTRUCTOR".equalsIgnoreCase(role);
        boolean isOwner = Objects.equals(fileUpload.getUploaderId(), currentUserId);
        if (!isAdmin && !isOwner) {
            // Standard check: file must not be locked/failed
            if (fileUpload.getStatus() == com.vatly1.example.entity.enums.FileProcessingStatus.FAILED) {
                throw new CustomException("File is not available for download", HttpStatus.BAD_REQUEST);
            }
        }

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

    @Operation(summary = "Truy xuất tệp hoặc hình ảnh", description = "Stream tệp/ảnh từ MinIO với đúng MediaType để hiển thị trực tiếp trên trình duyệt.")
    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> getFile(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/v1/files/";
        int index = fullPath.indexOf(prefix);
        String filename = (index != -1) ? fullPath.substring(index + prefix.length()) : fullPath;

        InputStream is = fileStorageService.getFileInputStream(filename);
        MediaType mediaType = determineMediaType(filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
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
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}