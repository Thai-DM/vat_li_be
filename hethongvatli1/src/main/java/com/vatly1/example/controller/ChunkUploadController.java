package com.vatly1.example.controller;

import com.vatly1.example.entity.User;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.exception.MissingChunksException;
import com.vatly1.example.model.request.MergeChunksRequest;
import com.vatly1.example.model.response.ChunkUploadResponse;
import com.vatly1.example.model.response.ChunkUploadStatusResponse;
import com.vatly1.example.model.response.IncompleteChunksResponse;
import com.vatly1.example.model.response.MergeChunksResponse;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IChunkUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files/chunks")
@RequiredArgsConstructor
@Tag(name = "Chunked File Upload", description = "APIs Tải lên file phân mảnh (chunked upload) và ghép file (merge)")
@SecurityRequirement(name = "bearerAuth")
public class ChunkUploadController {

    private final IChunkUploadService chunkUploadService;
    private final IUserRepository userRepository;

    @Operation(summary = "Tải lên một chunk của file", description = "FE chia file thành nhiều chunk (tối đa 100 KB/chunk) và gửi độc lập từng chunk.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("uploadId") UUID uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunk") MultipartFile chunk,
            HttpServletRequest request) {

        UUID currentUserId = resolveUserId(request);
        ChunkUploadResponse response = chunkUploadService.uploadChunk(
                uploadId, chunkIndex, totalChunks, fileName, chunk, currentUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ghép các chunk thành file hoàn chỉnh", description = "FE yêu cầu merge sau khi đã upload thành công tất cả các chunk.")
    @PostMapping("/merge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> mergeChunks(
            @Valid @RequestBody MergeChunksRequest mergeRequest,
            HttpServletRequest request) {

        UUID currentUserId = resolveUserId(request);
        String currentUserRole = resolveRole(request);
        try {
            MergeChunksResponse response = chunkUploadService.mergeChunks(
                    mergeRequest, currentUserId, currentUserRole);
            return ResponseEntity.ok(response);
        } catch (MissingChunksException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    IncompleteChunksResponse.builder()
                            .status("INCOMPLETE")
                            .message(ex.getMessage() != null ? ex.getMessage() : "Missing chunks")
                            .missingChunks(ex.getMissingChunks())
                            .build()
            );
        }
    }

    @Operation(summary = "Kiểm tra tiến độ upload chunk", description = "Xem danh sách các chunk đã nhận và các chunk còn thiếu.")
    @GetMapping("/{uploadId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChunkUploadStatusResponse> getStatus(
            @PathVariable UUID uploadId,
            HttpServletRequest request) {

        UUID currentUserId = resolveUserId(request);
        String currentUserRole = resolveRole(request);
        ChunkUploadStatusResponse response = chunkUploadService.getUploadStatus(
                uploadId, currentUserId, currentUserRole);
        return ResponseEntity.ok(response);
    }

    private UUID resolveUserId(HttpServletRequest request) {
        String userIdStr = (String) request.getAttribute("userId");
        if (userIdStr != null) {
            try {
                return UUID.fromString(userIdStr);
            } catch (IllegalArgumentException ignored) {}
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User u = userRepository.findByUsername(auth.getName());
            if (u != null) {
                return u.getUserId();
            }
        }
        throw new CustomException("User must be authenticated", HttpStatus.UNAUTHORIZED);
    }

    private String resolveRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role != null) return role;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5))
                    .findFirst()
                    .orElse("STUDENT");
        }
        return "STUDENT";
    }
}
