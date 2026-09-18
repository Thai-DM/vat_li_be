package com.vatly1.example.controller;

import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.service.ILogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Student Activity Logs", description = "APIs Ghi nhận nhật ký tương tác và hoạt động học tập của sinh viên")
@SecurityRequirement(name = "bearerAuth")
public class StudentActivityLogController {

    private final ILogService logService;

    @Operation(summary = "Lấy nhật ký hoạt động cá nhân của sinh viên", description = "Xem lịch sử các thao tác học tập, nộp bài và tương tác của sinh viên đang đăng nhập.")
    @GetMapping("/me/activity-logs")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<ActivityLog>>> getMyActivityLogs(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<ActivityLog> logs = logService.getStudentActivityLogs(currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<ActivityLog>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(logs)
                .build());
    }
}
