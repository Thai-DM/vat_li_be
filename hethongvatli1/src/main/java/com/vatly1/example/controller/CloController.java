package com.vatly1.example.controller;

import com.vatly1.example.model.dto.CloDTO;
import com.vatly1.example.model.request.CreateCloDTO;
import com.vatly1.example.model.request.UpdateCloDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.ICloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "CLO Management", description = "APIs Quản lý Chuẩn đầu ra học phần (Course Learning Outcomes - CLO) theo mô hình Subject-Centric")
@SecurityRequirement(name = "bearerAuth")
public class CloController {

    private final ICloService cloService;

    @Operation(summary = "Lấy danh sách CLO của môn học", description = "Lấy tất cả các chuẩn đầu ra (CLO) thuộc về môn học được chỉ định.")
    @GetMapping("/subjects/{subjectId}/clos")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<CloDTO>>> getClosBySubject(
            @PathVariable UUID subjectId,
            @RequestParam(required = false) Boolean activeOnly) {
        List<CloDTO> clos = cloService.getClosBySubject(subjectId, activeOnly);
        return ResponseEntity.ok(ApiResponse.<List<CloDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(clos)
                .build());
    }

    @Operation(summary = "Tạo CLO mới cho môn học", description = "Tạo mới một chuẩn đầu ra học phần thuộc môn học (Admin hoặc Giảng viên).")
    @PostMapping("/subjects/{subjectId}/clos")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CloDTO>> createClo(
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateCloDTO createDTO,
            HttpServletRequest request) {
        UUID currentUserId = extractUserId(request);
        String role = extractRole(request);
        CloDTO created = cloService.createClo(subjectId, createDTO, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CloDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(created)
                .build());
    }

    @Operation(summary = "Lấy thông tin chi tiết CLO theo ID", description = "Xem chi tiết chuẩn đầu ra CLO.")
    @GetMapping("/clos/{cloId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<CloDTO>> getCloById(@PathVariable UUID cloId) {
        CloDTO clo = cloService.getCloById(cloId);
        return ResponseEntity.ok(ApiResponse.<CloDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(clo)
                .build());
    }

    @Operation(summary = "Cập nhật thông tin CLO", description = "Chỉnh sửa mã CLO, mô tả hoặc thứ tự hiển thị.")
    @PutMapping("/clos/{cloId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CloDTO>> updateClo(
            @PathVariable UUID cloId,
            @Valid @RequestBody UpdateCloDTO updateDTO,
            HttpServletRequest request) {
        UUID currentUserId = extractUserId(request);
        String role = extractRole(request);
        CloDTO updated = cloService.updateClo(cloId, updateDTO, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<CloDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }

    @Operation(summary = "Bật/Tắt trạng thái hoạt động của CLO", description = "Kích hoạt hoặc vô hiệu hóa chuẩn đầu ra học phần.")
    @PatchMapping("/clos/{cloId}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CloDTO>> toggleCloActive(
            @PathVariable UUID cloId,
            @RequestParam boolean isActive,
            HttpServletRequest request) {
        UUID currentUserId = extractUserId(request);
        String role = extractRole(request);
        CloDTO toggled = cloService.toggleCloActive(cloId, isActive, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<CloDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(toggled)
                .build());
    }

    @Operation(summary = "Xóa CLO khỏi môn học", description = "Xóa chuẩn đầu ra CLO (bị chặn nếu đã gán câu hỏi trong ngân hàng).")
    @DeleteMapping("/clos/{cloId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteClo(
            @PathVariable UUID cloId,
            HttpServletRequest request) {
        UUID currentUserId = extractUserId(request);
        String role = extractRole(request);
        cloService.deleteClo(cloId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("CLO deleted successfully")
                .data(null)
                .build());
    }

    private UUID extractUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        if (attr != null) {
            return UUID.fromString(attr.toString());
        }
        return null;
    }

    private String extractRole(HttpServletRequest request) {
        Object attr = request.getAttribute("role");
        if (attr != null) {
            return attr.toString();
        }
        return null;
    }
}
