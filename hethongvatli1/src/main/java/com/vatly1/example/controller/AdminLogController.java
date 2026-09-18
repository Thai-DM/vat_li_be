package com.vatly1.example.controller;

import com.vatly1.example.dto.ApiResponse;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.entity.AuditLog;
import com.vatly1.example.service.ILogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Logs", description = "APIs Nhật ký kiểm toán bảo mật và hành động của Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class AdminLogController {

    private final ILogService logService;

    @Operation(summary = "Truy vấn nhật ký hoạt động hệ thống (Chỉ Admin)", description = "Lọc nhật ký hoạt động theo người dùng, loại hành động và khoảng thời gian.")
    @GetMapping("/activity-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLog>>> getActivityLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @ParameterObject Pageable pageable) {
        Page<ActivityLog> logs = logService.getAdminActivityLogs(userId, actionType, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ActivityLog>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(logs)
                .build());
    }

    @Operation(summary = "Truy vấn nhật ký kiểm toán bảo mật (Chỉ Admin)", description = "Lọc nhật ký kiểm toán thay đổi dữ liệu theo thực thể và người dùng.")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) UUID userId,
            @ParameterObject Pageable pageable) {
        Page<AuditLog> logs = logService.getAdminAuditLogs(entity, userId, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<AuditLog>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(logs)
                .build());
    }
}
