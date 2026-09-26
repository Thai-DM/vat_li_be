package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ClassScheduleDTO;
import com.vatly1.example.model.request.CreateClassScheduleDTO;
import com.vatly1.example.model.request.UpdateClassScheduleDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IClassScheduleService;
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
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
@Tag(name = "Class Schedule Management", description = "APIs Quản lý thời khóa biểu, lịch học lớp học phần")
@SecurityRequirement(name = "bearerAuth")
public class ClassScheduleController {

    private final IClassScheduleService scheduleService;

    @Operation(summary = "Lấy danh sách lịch học của một lớp", description = "Xem thời khóa biểu của lớp học phần. Hỗ trợ Giảng viên, Trợ giảng, Sinh viên trong lớp và Quản trị viên.")
    @GetMapping("/{classId}/schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ClassScheduleDTO>>> getSchedulesByClassId(
            HttpServletRequest request,
            @PathVariable UUID classId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByClassId(classId, currentUserId, role)));
    }

    @Operation(summary = "Thêm lịch học cho lớp", description = "Tạo một buổi học mới trong tuần cho lớp (Chỉ Giảng viên phụ trách hoặc Admin).")
    @PostMapping("/{classId}/schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ClassScheduleDTO>> createSchedule(
            HttpServletRequest request,
            @PathVariable UUID classId,
            @Valid @RequestBody CreateClassScheduleDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(scheduleService.createSchedule(classId, dto, currentUserId, role), "Tạo lịch học thành công"));
    }

    @Operation(summary = "Cập nhật thông tin lịch học", description = "Sửa thứ, tiết, giờ, phòng học hoặc loại buổi học (Chỉ Giảng viên phụ trách hoặc Admin).")
    @PutMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ClassScheduleDTO>> updateSchedule(
            HttpServletRequest request,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateClassScheduleDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(scheduleService.updateSchedule(scheduleId, dto, currentUserId, role), "Cập nhật lịch học thành công"));
    }

    @Operation(summary = "Xóa lịch học", description = "Xóa buổi học khỏi thời khóa biểu của lớp (Chỉ Giảng viên phụ trách hoặc Admin).")
    @DeleteMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            HttpServletRequest request,
            @PathVariable UUID scheduleId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        scheduleService.deleteSchedule(scheduleId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa lịch học thành công"));
    }
}
