package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File & Media Storage", description = "APIs Lưu trữ và Truy xuất tệp, tài liệu, hình ảnh qua MinIO Object Storage")
public class FileController {

    private final IFileStorageService fileStorageService;

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