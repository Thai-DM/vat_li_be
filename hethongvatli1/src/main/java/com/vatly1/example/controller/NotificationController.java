package com.vatly1.example.controller;

import com.vatly1.example.model.dto.NotificationDTO;
import com.vatly1.example.model.dto.NotificationSummaryDTO;
import com.vatly1.example.model.request.CreateNotificationDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "APIs Hệ thống thông báo: bài thi mới, điểm số, nhắc nhở lịch học và thông báo lớp")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final INotificationService notificationService;

    @Operation(summary = "Lấy danh sách thông báo của tôi", description = "Lấy danh sách thông báo phân trang, hỗ trợ lọc thông báo chưa đọc.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationDTO>>> getMyNotifications(
            HttpServletRequest request,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMyNotifications(currentUserId, unreadOnly, page, size)));
    }

    @Operation(summary = "Lấy tóm tắt thông báo", description = "Lấy số lượng thông báo chưa đọc và danh sách 5 thông báo mới nhất cho chuông thông báo.")
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationSummaryDTO>> getMyNotificationSummary(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMyNotificationSummary(currentUserId)));
    }

    @Operation(summary = "Đánh dấu thông báo là đã đọc", description = "Cập nhật trạng thái isRead = true cho một thông báo.")
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDTO>> markAsRead(
            HttpServletRequest request,
            @PathVariable UUID id) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAsRead(id, currentUserId), "Đã đánh dấu thông báo là đã đọc"));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc", description = "Cập nhật toàn bộ thông báo của người dùng hiện tại sang trạng thái đã đọc.")
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    @Operation(summary = "Xóa thông báo", description = "Xóa một thông báo khỏi danh sách của người dùng.")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            HttpServletRequest request,
            @PathVariable UUID id) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        notificationService.deleteNotification(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa thông báo thành công"));
    }

    @Operation(summary = "Gửi thông báo tới toàn thể sinh viên trong lớp", description = "Giảng viên hoặc Quản trị viên gửi thông báo đến các sinh viên đang học trong lớp.")
    @PostMapping("/classes/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendNotificationToClass(
            HttpServletRequest request,
            @PathVariable UUID classId,
            @Valid @RequestBody CreateNotificationDTO dto) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        int count = notificationService.sendNotificationToClass(classId, dto, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("sentCount", count), "Gửi thông báo thành công tới " + count + " sinh viên"));
    }

    @Operation(summary = "Tạo thông báo nhắc lịch học sắp tới", description = "Kiểm tra thời khóa biểu của các lớp sinh viên đang học và sinh thông báo nhắc nhở nếu có buổi học hôm nay hoặc ngày mai.")
    @PostMapping("/reminders/generate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateScheduleReminders(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        int generated = notificationService.generateUpcomingScheduleReminders(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("generatedReminders", generated),
                generated > 0 ? "Đã tạo " + generated + " thông báo nhắc lịch học mới" : "Không có lịch học mới cần nhắc nhở"));
    }
}
