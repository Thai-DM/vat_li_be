package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.response.MatrixValidationResultDTO;
import com.vatly1.example.service.IExamMatrixService;
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
@RequestMapping("/api/v1/exam-matrices")
@RequiredArgsConstructor
@Tag(name = "Exam Matrix Management", description = "APIs Quản lý ma trận đề thi (Cấu trúc chương/mục, phân bố độ khó nhận thức Bloom)")
@SecurityRequirement(name = "bearerAuth")
public class ExamMatrixController {

    private final IExamMatrixService examMatrixService;

    @Operation(summary = "Lấy danh sách ma trận đề thi", description = "Hỗ trợ lọc theo subjectId hoặc classId để phục vụ dropdown khi tạo đề thi.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<List<ExamMatrixDTO>>> getExamMatrices(
            @RequestParam(value = "classId", required = false) UUID classId,
            @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        List<ExamMatrixDTO> matrices = examMatrixService.getExamMatrices(classId, subjectId);
        return ResponseEntity.ok(ApiResponse.success(matrices));
    }

    @Operation(summary = "Tạo ma trận đề thi mới", description = "Tạo ma trận đề thi kèm tỉ lệ phân bổ câu hỏi theo từng chương mục và mức độ nhận thức.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> createExamMatrix(
            @Valid @RequestBody CreateExamMatrixDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO created = examMatrixService.createExamMatrix(dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamMatrixDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo ma trận đề thi thành công")
                .data(created)
                .build());
    }

    @Operation(summary = "Xem chi tiết ma trận đề thi", description = "Xem chi tiết cấu trúc ma trận kèm thống kê tỉ lệ phân bố theo chương và mức độ khó (Bloom).")
    @GetMapping("/{matrixId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> getExamMatrixById(@PathVariable UUID matrixId) {
        ExamMatrixDTO matrix = examMatrixService.getExamMatrixById(matrixId);
        return ResponseEntity.ok(ApiResponse.success(matrix));
    }

    @Operation(summary = "Cập nhật ma trận đề thi", description = "Chỉnh sửa thông tin và danh sách chi tiết phân bổ câu hỏi của ma trận.")
    @PutMapping("/{matrixId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> updateExamMatrix(
            @PathVariable UUID matrixId,
            @Valid @RequestBody UpdateExamMatrixDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO updated = examMatrixService.updateExamMatrix(matrixId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật ma trận đề thi thành công"));
    }

    @Operation(summary = "Xóa ma trận đề thi", description = "Xóa ma trận đề thi chưa được sử dụng bởi bất kỳ kỳ thi nào.")
    @DeleteMapping("/{matrixId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteExamMatrix(
            @PathVariable UUID matrixId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        examMatrixService.deleteExamMatrix(matrixId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa ma trận đề thi thành công"));
    }

    @Operation(summary = "Kiểm tra ngân hàng câu hỏi theo ma trận đề", description = "Xác thực ngân hàng câu hỏi có đủ số lượng theo từng chủ đề và độ khó yêu cầu hay không trước khi tạo/sinh đề.")
    @PostMapping("/{matrixId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<MatrixValidationResultDTO>> validateExamMatrix(@PathVariable UUID matrixId) {
        MatrixValidationResultDTO result = examMatrixService.validateExamMatrix(matrixId);
        return ResponseEntity.ok(ApiResponse.success(result, "Kiểm tra tính hợp lệ ngân hàng câu hỏi hoàn tất"));
    }
}
