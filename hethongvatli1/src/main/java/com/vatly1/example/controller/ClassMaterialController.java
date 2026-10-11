package com.vatly1.example.controller;

import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.model.request.CreateLearningMaterialDTO;
import com.vatly1.example.model.response.ApiResponse;
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
@RequestMapping("/api/v1/classes/{classId}")
@RequiredArgsConstructor
@Tag(name = "Class Learning Material", description = "APIs Quản lý học liệu riêng theo lớp học")
@SecurityRequirement(name = "bearerAuth")
public class ClassMaterialController {

    private final ILearningMaterialService learningMaterialService;

    @Operation(summary = "Lấy danh sách học liệu theo lớp học", description = "Trả về danh sách học liệu dùng chung (SHARED) của môn và học liệu riêng (CLASS) của lớp.")
    @GetMapping("/materials")
    public ResponseEntity<ApiResponse<List<LearningMaterialDTO>>> getMaterialsByClass(
            @PathVariable UUID classId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) MaterialType type,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        List<LearningMaterialDTO> materials = learningMaterialService.getMaterialsByClass(classId, topicId, type, role);
        return ResponseEntity.ok(ApiResponse.<List<LearningMaterialDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(materials)
                .build());
    }

    @Operation(summary = "Tạo học liệu riêng cho lớp học", description = "Giảng viên phụ trách tải lên học liệu riêng gắn với lớp học và chủ đề.")
    @PostMapping(value = "/topics/{topicId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LearningMaterialDTO>> createClassMaterial(
            @PathVariable UUID classId,
            @PathVariable UUID topicId,
            @Valid @ModelAttribute CreateLearningMaterialDTO createDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        LearningMaterialDTO created = learningMaterialService.createClassMaterial(classId, topicId, createDTO, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<LearningMaterialDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(created)
                .build());
    }
}
