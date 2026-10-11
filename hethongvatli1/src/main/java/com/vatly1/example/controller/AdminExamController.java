package com.vatly1.example.controller;

import com.vatly1.example.model.request.BulkProvisionFinalExamDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.response.BulkProvisionFinalExamResultDTO;
import com.vatly1.example.service.IExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exams")
@RequiredArgsConstructor
@Tag(name = "Admin Exam Management", description = "APIs Quản trị viên quản lý kỳ thi (Bulk Provisioning, Điều phối đề thi tập trung)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExamController {

    private final IExamService examService;

    @Operation(summary = "Khởi tạo kỳ thi cuối kỳ hàng loạt (Bulk Provision FINAL)",
               description = "Khởi tạo kỳ thi FINAL cho danh sách lớp học của cùng một môn học dựa trên Common FINAL Matrix đã PUBLISHED.")
    @PostMapping("/bulk-provision-final")
    public ResponseEntity<ApiResponse<BulkProvisionFinalExamResultDTO>> bulkProvisionFinal(
            @Valid @RequestBody BulkProvisionFinalExamDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        BulkProvisionFinalExamResultDTO result = examService.bulkProvisionFinalExams(dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<BulkProvisionFinalExamResultDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Khởi tạo kỳ thi cuối kỳ hàng loạt (Bulk Provisioning) thành công")
                .data(result)
                .build());
    }
}
