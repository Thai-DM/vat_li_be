package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
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
@RequestMapping("/api/v1/admin/exam-matrices")
@RequiredArgsConstructor
@Tag(name = "Admin Exam Matrix Management", description = "APIs Quản trị viên quản lý Ma trận Đề thi Cuối kỳ Dùng chung (Common FINAL Matrix Lifecycle)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExamMatrixController {

    private final IExamMatrixService examMatrixService;

    @Operation(summary = "Tạo ma trận đề thi cuối kỳ (FINAL) dùng chung", description = "Chỉ Quản trị viên (ADMIN) mới có quyền tạo Common FINAL Matrix.")
    @PostMapping("/final")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> createFinalExamMatrix(
            @Valid @RequestBody CreateExamMatrixDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO created = examMatrixService.createFinalExamMatrix(dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamMatrixDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo ma trận đề thi cuối kỳ (FINAL) thành công")
                .data(created)
                .build());
    }

    @Operation(summary = "Xác thực tính khả thi ngân hàng câu hỏi", description = "Kiểm tra tính khả thi của ngân hàng câu hỏi theo thuật toán Max-Flow Dinic.")
    @PostMapping("/{matrixId}/validate")
    public ResponseEntity<ApiResponse<MatrixValidationResultDTO>> validateExamMatrix(@PathVariable UUID matrixId) {
        MatrixValidationResultDTO result = examMatrixService.validateMatrixQuestionAvailability(matrixId);
        return ResponseEntity.ok(ApiResponse.success(result, "Kiểm tra tính khả thi ngân hàng câu hỏi hoàn tất"));
    }

    @Operation(summary = "Công bố ma trận đề thi (PUBLISH)", description = "Chuyển ma trận từ DRAFT sang PUBLISHED và khóa cấu hình (isLocked = true).")
    @PostMapping("/{matrixId}/publish")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> publishMatrix(
            @PathVariable UUID matrixId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO published = examMatrixService.publishMatrix(matrixId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(published, "Công bố (PUBLISH) ma trận đề thi thành công"));
    }

    @Operation(summary = "Lưu trữ ma trận đề thi (ARCHIVE)", description = "Chuyển ma trận từ PUBLISHED sang ARCHIVED. Ma trận này không còn dùng để sinh đề thi mới.")
    @PostMapping("/{matrixId}/archive")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> archiveMatrix(
            @PathVariable UUID matrixId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO archived = examMatrixService.archiveMatrix(matrixId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(archived, "Lưu trữ (ARCHIVE) ma trận đề thi thành công"));
    }

    @Operation(summary = "Nhân bản phiên bản ma trận đề thi (CLONE REVISION)", description = "Tạo phiên bản mới (revisionNumber + 1) ở trạng thái DRAFT từ ma trận hiện có.")
    @PostMapping("/{matrixId}/clone-revision")
    public ResponseEntity<ApiResponse<ExamMatrixDTO>> cloneMatrixRevision(
            @PathVariable UUID matrixId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamMatrixDTO cloned = examMatrixService.cloneMatrixRevision(matrixId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamMatrixDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Nhân bản phiên bản ma trận (CLONE REVISION) thành công")
                .data(cloned)
                .build());
    }

    @Operation(summary = "Lấy danh sách ma trận đề thi theo môn học", description = "Lấy toàn bộ ma trận thuộc một Subject hoặc toàn hệ thống.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamMatrixDTO>>> getExamMatrices(
            @RequestParam(value = "subjectId", required = false) UUID subjectId) {
        List<ExamMatrixDTO> matrices = examMatrixService.getMatricesBySubject(subjectId);
        return ResponseEntity.ok(ApiResponse.success(matrices));
    }
}
