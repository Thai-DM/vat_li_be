package com.vatly1.example.controller;

import com.vatly1.example.model.dto.ClassDTO;
import com.vatly1.example.model.dto.ClassSettingsDTO;
import com.vatly1.example.model.request.UpdateClassSettingsDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IClassService;
import com.vatly1.example.service.IClassSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instructor/classes")
@RequiredArgsConstructor
@Tag(name = "Instructor Class Management", description = "APIs dành cho Giảng viên quản lý và cấu hình các lớp học phần được phân công")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorClassController {

    private final IClassService classService;
    private final IClassSettingsService classSettingsService;
    private final com.vatly1.example.service.IPracticeRecommendationService practiceRecommendationService;

    @Operation(summary = "Lấy danh sách lớp phụ trách của giảng viên", description = "Chỉ trả các lớp mà giảng viên hiện tại thực sự được phân công.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ClassDTO>>> getMyAssignedClasses(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        Page<ClassDTO> classes = classService.getInstructorClasses(currentUserId, page, size);
        return ResponseEntity.ok(ApiResponse.success(classes));
    }

    @Operation(summary = "Lấy chi tiết một lớp học phụ trách", description = "Xem chi tiết lớp học được phân công (Kiểm tra quyền sở hữu trực tiếp tại Service).")
    @GetMapping("/{classId}")
    public ResponseEntity<ApiResponse<ClassDTO>> getMyAssignedClassById(
            HttpServletRequest request,
            @PathVariable UUID classId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ClassDTO dto = classService.getInstructorClassById(classId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Lấy cấu hình học tập của lớp phụ trách", description = "Xem cấu hình hoạt động luyện tập, quiz của lớp được phân công.")
    @GetMapping("/{classId}/settings")
    public ResponseEntity<ApiResponse<ClassSettingsDTO>> getAssignedClassSettings(
            HttpServletRequest request,
            @PathVariable UUID classId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        ClassSettingsDTO settings = classSettingsService.getClassSettings(classId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @Operation(summary = "Cập nhật cấu hình học tập của lớp phụ trách", description = "Bật/tắt hoạt động luyện tập, quiz, số lượt làm bài của lớp.")
    @PutMapping("/{classId}/settings")
    public ResponseEntity<ApiResponse<ClassSettingsDTO>> updateAssignedClassSettings(
            HttpServletRequest request,
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassSettingsDTO dto) {
        UUID currentUserId = resolveUserId(request);
        String role = (String) request.getAttribute("role");
        ClassSettingsDTO settings = classSettingsService.updateClassSettings(classId, dto, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(settings, "Cập nhật cấu hình lớp học thành công"));
    }

    @Operation(summary = "Thống kê điểm yếu của sinh viên trong lớp phụ trách", description = "Xem tỷ lệ sinh viên cần củng cố kiến thức theo từng chủ đề trong lớp.")
    @GetMapping("/{classId}/weak-topics-summary")
    public ResponseEntity<ApiResponse<com.vatly1.example.model.dto.ClassWeakTopicsSummaryDTO>> getClassWeakTopicsSummary(
            HttpServletRequest request,
            @PathVariable UUID classId) {
        UUID currentUserId = resolveUserId(request);
        String role = (String) request.getAttribute("role");
        com.vatly1.example.model.dto.ClassWeakTopicsSummaryDTO summary = practiceRecommendationService.getClassWeakTopicsSummary(classId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    private UUID resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        if (attr instanceof UUID u) return u;
        if (attr != null) {
            String s = attr.toString().trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 2) {
                s = s.substring(1, s.length() - 1).trim();
            }
            if (s.length() == 36) {
                try { return UUID.fromString(s); } catch (Exception ignored) {}
            }
        }
        throw new com.vatly1.example.exception.CustomException("User not authenticated", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}
