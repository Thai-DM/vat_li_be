package com.vatly1.example.controller;

import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.request.CreateLearningMaterialDTO;
import com.vatly1.example.dto.LearningMaterialDTO;
import com.vatly1.example.service.ILearningMaterialService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topics/{topicId}/materials")
@RequiredArgsConstructor
@Tag(name = "Learning Material", description = "APIs Quản lý học liệu số đã được Bộ môn phê duyệt")
@SecurityRequirement(name = "bearerAuth")
public class LearningMaterialController {

    private final ILearningMaterialService learningMaterialService;

    @Operation(summary = "Lấy danh sách học liệu theo chương mục", description = "Trả về danh sách tài liệu số (PDF, Video, Bài giảng) thuộc chương mục.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LearningMaterialDTO>>> getMaterialsByTopic(
            @PathVariable UUID topicId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        List<LearningMaterialDTO> materials = learningMaterialService.getMaterialsByTopic(topicId, role);
        return ResponseEntity.ok(ApiResponse.<List<LearningMaterialDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(materials)
                .build());
    }

    @Operation(summary = "Lấy chi tiết học liệu theo ID", description = "Xem nội dung chi tiết hoặc link truy cập tài liệu số.")
    @GetMapping("/{materialId}")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> getMaterialById(
            @PathVariable UUID topicId,
            @PathVariable UUID materialId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        LearningMaterialDTO material = learningMaterialService.getMaterialById(materialId, role);
        return ResponseEntity.ok(ApiResponse.<LearningMaterialDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(material)
                .build());
    }

    @Operation(summary = "Tạo học liệu số mới", description = "Tải lên tài liệu học tập mới (File hoặc URL liên kết).")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> createMaterial(
            @PathVariable UUID topicId,
            @Valid @ModelAttribute CreateLearningMaterialDTO createDTO,
            HttpServletRequest request) {
        createDTO.setTopicId(topicId);
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        
        LearningMaterialDTO created = learningMaterialService.createMaterial(createDTO, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<LearningMaterialDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(created)
                .build());
    }

    @Operation(summary = "Cập nhật học liệu số", description = "Chỉnh sửa thông tin, tệp đính kèm hoặc nội dung bài giảng.")
    @PutMapping(value = "/{materialId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> updateMaterial(
            @PathVariable UUID topicId,
            @PathVariable UUID materialId,
            @Valid @ModelAttribute CreateLearningMaterialDTO updateDTO,
            HttpServletRequest request) {
        updateDTO.setTopicId(topicId);
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        
        LearningMaterialDTO updated = learningMaterialService.updateMaterial(materialId, updateDTO, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<LearningMaterialDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }

    @Operation(summary = "Bộ môn phê duyệt học liệu số", description = "Kiểm duyệt và xuất bản học liệu cho sinh viên truy cập.")
    @PutMapping("/{materialId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> approveMaterial(
            @PathVariable UUID topicId,
            @PathVariable UUID materialId) {
        LearningMaterialDTO approved = learningMaterialService.approveMaterial(materialId);
        return ResponseEntity.ok(ApiResponse.<LearningMaterialDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(approved)
                .build());
    }

    @Operation(summary = "Xóa học liệu số", description = "Gỡ bỏ tài liệu học tập khỏi chương mục.")
    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(
            @PathVariable UUID topicId,
            @PathVariable UUID materialId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        
        learningMaterialService.deleteMaterial(materialId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(null)
                .build());
    }
}
