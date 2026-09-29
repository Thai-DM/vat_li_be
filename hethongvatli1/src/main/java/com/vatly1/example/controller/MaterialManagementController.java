package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.ILearningMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Tag(name = "Learning Material Management", description = "APIs Quản trị học liệu số và công cụ bảo trì hệ thống")
@SecurityRequirement(name = "bearerAuth")
public class MaterialManagementController {

    private final ILearningMaterialService learningMaterialService;

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
