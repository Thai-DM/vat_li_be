package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.request.CreateSubjectDTO;
import com.vatly1.example.model.dto.SubjectDTO;
import com.vatly1.example.model.request.UpdateSubjectDTO;
import com.vatly1.example.service.ISubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
@Tag(name = "Subject Management", description = "APIs Quản lý danh mục môn học (Vật lý 1 và mở rộng Khoa Cơ bản 1)")
@SecurityRequirement(name = "bearerAuth")
public class SubjectController {

    private final ISubjectService subjectService;

    @Operation(summary = "Lấy danh sách môn học", description = "Lấy danh sách phân trang tất cả môn học. Hỗ trợ lọc theo trạng thái (isActive).")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SubjectDTO>>> getSubjects(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(subjectService.getSubjects(isActive, page, size)));
    }

    @Operation(summary = "Lấy chi tiết môn học", description = "Lấy thông tin chi tiết của 1 môn học bằng ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubjectDTO>> getSubjectById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(subjectService.getSubjectById(id)));
    }

    @Operation(summary = "Tạo môn học mới (Chỉ Admin)", description = "Tạo một môn học mới.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<SubjectDTO>> createSubject(@Valid @RequestBody CreateSubjectDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(subjectService.createSubject(dto)));
    }

    @Operation(summary = "Cập nhật môn học (Chỉ Admin)", description = "Cập nhật thông tin môn học (tên, mô tả). Không được sửa mã môn.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SubjectDTO>> updateSubject(@PathVariable UUID id, @Valid @RequestBody UpdateSubjectDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(subjectService.updateSubject(id, dto)));
    }

    @Operation(summary = "Bật/Tắt môn học (Chỉ Admin)", description = "Toggle trạng thái isActive của môn học (Xóa mềm).")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/toggle-status")
    public ResponseEntity<ApiResponse<SubjectDTO>> toggleSubjectStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(subjectService.toggleSubjectStatus(id)));
    }
}