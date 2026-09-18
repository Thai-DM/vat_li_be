package com.vatly1.example.controller;

import com.vatly1.example.dto.ApiResponse;
import com.vatly1.example.dto.DashboardSnapshotDTO;
import com.vatly1.example.service.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "APIs Bảng điều khiển tổng hợp cho Giảng viên và Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final IDashboardService dashboardService;

    @Operation(summary = "Lấy dữ liệu bảng điều khiển tổng hợp của lớp", description = "Xem biểu đồ phân bố điểm, tỉ lệ hoàn thành học phần và các chỉ số then chốt.")
    @GetMapping("/class/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSnapshotDTO>> getClassDashboard(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        DashboardSnapshotDTO data = dashboardService.getClassDashboard(id, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<DashboardSnapshotDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Lấy dữ liệu bảng điều khiển của một sinh viên trong lớp", description = "Giảng viên xem chi tiết quá trình học tập của một sinh viên trong lớp.")
    @GetMapping("/class/{id}/student/{studentId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSnapshotDTO>> getStudentDashboard(
            @PathVariable UUID id,
            @PathVariable UUID studentId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        DashboardSnapshotDTO data = dashboardService.getStudentDashboard(id, studentId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<DashboardSnapshotDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Lấy bảng điều khiển học tập cá nhân của tôi", description = "Sinh viên theo dõi điểm số, xếp hạng và lộ trình hoàn thành của bản thân.")
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<DashboardSnapshotDTO>> getMyDashboard(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        DashboardSnapshotDTO data = dashboardService.getMyDashboard(currentUserId);
        return ResponseEntity.ok(ApiResponse.<DashboardSnapshotDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Tạo lại dữ liệu bảng điều khiển cho lớp học (Chỉ Admin)", description = "Tính toán và cập nhật lại dữ liệu snapshot bảng điều khiển tức thời.")
    @PostMapping("/class/{id}/regenerate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSnapshotDTO>> regenerateClassSnapshot(@PathVariable UUID id) {
        DashboardSnapshotDTO data = dashboardService.regenerateClassSnapshot(id);
        return ResponseEntity.ok(ApiResponse.<DashboardSnapshotDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Snapshot regenerated successfully")
                .data(data)
                .build());
    }
}
