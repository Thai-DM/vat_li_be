package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.request.CreateSemesterDTO;
import com.vatly1.example.model.dto.SemesterDTO;
import com.vatly1.example.model.request.UpdateSemesterDTO;
import com.vatly1.example.service.ISemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
@Tag(name = "Semester Management", description = "APIs Quản lý học kỳ, năm học")
@SecurityRequirement(name = "bearerAuth")
public class SemesterController {

    private final ISemesterService semesterService;

    @Operation(summary = "Lấy danh sách học kỳ", description = "Lấy danh sách tất cả học kỳ, được sắp xếp mới nhất lên đầu.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SemesterDTO>>> getAllSemesters() {
        return ResponseEntity.ok(ApiResponse.success(semesterService.getAllSemesters()));
    }

    @Operation(summary = "Lấy chi tiết học kỳ", description = "Lấy thông tin chi tiết của 1 học kỳ bằng ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SemesterDTO>> getSemesterById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(semesterService.getSemesterById(id)));
    }

    @Operation(summary = "Tạo học kỳ mới (Chỉ Admin)", description = "Tạo một học kỳ mới. Mặc định isCurrent = false.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<SemesterDTO>> createSemester(@Valid @RequestBody CreateSemesterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(semesterService.createSemester(dto)));
    }

    @Operation(summary = "Cập nhật học kỳ (Chỉ Admin)", description = "Cập nhật thông tin học kỳ. Không cho phép trùng tên học kỳ trong cùng năm học.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SemesterDTO>> updateSemester(@PathVariable UUID id, @Valid @RequestBody UpdateSemesterDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(semesterService.updateSemester(id, dto)));
    }

    @Operation(summary = "Đánh dấu học kỳ hiện tại (Chỉ Admin)", description = "Đánh dấu học kỳ này là học kỳ hiện tại. Các học kỳ khác sẽ tự động chuyển về false.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/set-current")
    public ResponseEntity<ApiResponse<SemesterDTO>> setCurrentSemester(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(semesterService.setCurrentSemester(id)));
    }
}