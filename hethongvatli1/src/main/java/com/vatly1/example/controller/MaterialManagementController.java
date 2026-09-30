package com.vatly1.example.controller;

import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.ILearningMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Tag(name = "Learning Material Management", description = "APIs Quản trị học liệu số và công cụ bảo trì hệ thống")
@SecurityRequirement(name = "bearerAuth")
public class MaterialManagementController {

    private final ILearningMaterialService learningMaterialService;

    @Operation(summary = "Lấy danh sách tất cả học liệu", description = "Xem danh sách tài liệu học tập, có thể lọc theo topicId hoặc bỏ trống để lấy tất cả.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LearningMaterialDTO>>> getAllMaterials(
            @RequestParam(required = false) UUID topicId,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        List<LearningMaterialDTO> materials = learningMaterialService.getAllMaterials(topicId, role);
        return ResponseEntity.ok(ApiResponse.<List<LearningMaterialDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(materials)
                .build());
    }

    @Operation(summary = "Lấy chi tiết học liệu theo ID", description = "Xem thông tin chi tiết hoặc file học liệu trực tiếp bằng ID mà không cần truyền topicId.")
    @GetMapping("/{materialId}")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> getMaterialById(
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

    @Operation(summary = "Migration dữ liệu loại học liệu cũ", description = "Chuyển đổi các định dạng học liệu cũ (DOCUMENT, DOC, DOCX) sang PDF theo chuẩn mới. Chỉ dành cho ADMIN.")
    @PostMapping("/migrate-legacy-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> migrateLegacyTypes() {
        int updated = learningMaterialService.migrateLegacyTypes();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .status(200)
                .message("Legacy material types migrated successfully")
                .data(Map.of(
                        "updatedRecords", updated,
                        "targetType", "PDF"
                ))
                .build());
    }
}
