package com.vatly1.example.controller;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.ActivityLog;
import com.vatly1.example.service.IClassEnrollmentService;
import com.vatly1.example.service.IClassService;
import com.vatly1.example.service.IClassStaffService;
import com.vatly1.example.service.ILogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
@Tag(name = "Class Management", description = "APIs Quản lý lớp học, phân công giảng viên, danh sách lớp")
@SecurityRequirement(name = "bearerAuth")
public class ClassController {

    private final IClassService classService;
    private final IClassStaffService classStaffService;
    private final IClassEnrollmentService enrollmentService;
    private final ILogService logService;

    // --- CLASS MANAGEMENT ---

    @Operation(summary = "Lấy danh sách lớp học", description = "Lấy danh sách lớp học. Trả về theo phân quyền của người gọi (Admin thấy hết, GV thấy lớp của mình).")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<Page<ClassDTO>>> getClasses(
            HttpServletRequest request,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(classService.getClasses(subjectId, semesterId, status, page, size, currentUserId, role)));
    }

    @Operation(summary = "Lấy chi tiết lớp học", description = "Xem chi tiết một lớp học (Có kiểm tra quyền sở hữu).")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<ClassDTO>> getClassById(HttpServletRequest request, @PathVariable UUID id) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(classService.getClassById(id, currentUserId, role)));
    }

    @Operation(summary = "Tạo lớp học mới", description = "Tạo lớp học mới. Nếu là INSTRUCTOR tạo, họ tự động làm chủ lớp.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ClassDTO>> createClass(HttpServletRequest request, @Valid @RequestBody CreateClassDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(classService.createClass(dto, currentUserId)));
    }

    @Operation(summary = "Cập nhật lớp học", description = "Sửa mã lớp, số lượng SV tối đa. (Chỉ Admin hoặc chủ lớp)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ClassDTO>> updateClass(HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody UpdateClassDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(classService.updateClass(id, dto, currentUserId, role)));
    }

    @Operation(summary = "Đổi trạng thái lớp học", description = "Chuyển trạng thái lớp: DRAFT -> ACTIVE -> COMPLETED -> ARCHIVED.")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ClassDTO>> updateClassStatus(HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody UpdateClassStatusDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(classService.updateClassStatus(id, dto, currentUserId, role)));
    }

    // --- STAFF MANAGEMENT ---

    @Operation(summary = "Danh sách nhân sự của lớp", description = "Lấy danh sách Giảng viên/Trợ giảng được phân công vào lớp.")
    @GetMapping("/{id}/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<List<ClassStaffDTO>>> getClassStaff(HttpServletRequest request, @PathVariable UUID id) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(classStaffService.getClassStaff(id, currentUserId, role)));
    }

    @Operation(summary = "Phân công nhân sự", description = "Thêm một Giảng viên/Trợ giảng vào lớp. (Chỉ Admin hoặc chủ lớp)")
    @PostMapping("/{id}/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> assignStaff(HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody AssignStaffDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        classStaffService.assignStaff(id, dto, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, "Staff assigned successfully"));
    }

    @Operation(summary = "Xóa nhân sự khỏi lớp", description = "Xóa Giảng viên/Trợ giảng khỏi lớp. (Chỉ Admin hoặc chủ lớp)")
    @DeleteMapping("/{id}/staff/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> removeStaff(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID userId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        classStaffService.removeStaff(id, userId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Staff removed successfully"));
    }

    // --- ENROLLMENT MANAGEMENT ---

    @Operation(summary = "Danh sách sinh viên của lớp", description = "Lấy danh sách sinh viên đã ghi danh vào lớp.")
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<Page<EnrollmentDTO>>> getClassStudents(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.getClassStudents(id, status, page, size, currentUserId, role)));
    }

    @Operation(summary = "Ghi danh một sinh viên", description = "Thêm 1 sinh viên vào lớp. (Chỉ Admin hoặc chủ lớp)")
    @PostMapping("/{id}/enroll-single")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> enrollSingleStudent(HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody SingleEnrollmentDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        enrollmentService.enrollSingleStudent(id, dto, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, "Student enrolled successfully"));
    }

    @Operation(summary = "Ghi danh hàng loạt", description = "Thêm danh sách sinh viên vào lớp. Tối đa 500 sinh viên. (Chỉ Admin hoặc chủ lớp)")
    @PostMapping("/{id}/enroll-bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> enrollBulkStudents(HttpServletRequest request, @PathVariable UUID id, @Valid @RequestBody BulkEnrollmentDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        enrollmentService.enrollBulkStudents(id, dto, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, "Students enrolled successfully"));
    }

    @Operation(summary = "Đổi trạng thái ghi danh", description = "Thay đổi trạng thái ghi danh (ACTIVE, DROPPED, COMPLETED).")
    @PutMapping("/{id}/students/{studentId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> updateEnrollmentStatus(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID studentId, @Valid @RequestBody UpdateEnrollmentStatusDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        enrollmentService.updateEnrollmentStatus(id, studentId, dto, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Enrollment status updated successfully"));
    }

    @Operation(summary = "Xóa sinh viên khỏi lớp", description = "Xóa sinh viên khỏi lớp hoàn toàn (Xóa cứng).")
    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> removeStudent(HttpServletRequest request, @PathVariable UUID id, @PathVariable UUID studentId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        enrollmentService.removeStudent(id, studentId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Student removed successfully"));
    }

    @Operation(summary = "Xem nhật ký hoạt động của lớp")
    @GetMapping("/{id}/activity-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<ActivityLog>>> getClassActivityLogs(
            HttpServletRequest request,
            @PathVariable UUID id) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<ActivityLog> logs = logService.getClassActivityLogs(id, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}