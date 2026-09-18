package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.dto.LearningProgressDTO;
import com.vatly1.example.model.request.UpdateLearningProgressDTO;
import com.vatly1.example.service.ILearningProgressService;
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
@RequestMapping("/api/v1/students/me/progress")
@RequiredArgsConstructor
@Tag(name = "Student Progress", description = "APIs Thống kê chỉ số hoàn thành tổng thể của sinh viên trong lớp")
@SecurityRequirement(name = "bearerAuth")
public class StudentProgressController {

    private final ILearningProgressService progressService;

    @Operation(summary = "Lấy tiến độ học tập của tôi theo lớp", description = "Sinh viên xem danh sách học liệu đã hoàn thành và tiến độ phần trăm theo lớp học.")
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<LearningProgressDTO>>> getMyProgress(
            @RequestParam UUID classId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        
        List<LearningProgressDTO> progress = progressService.getMyProgress(currentUserId, classId);
        return ResponseEntity.ok(ApiResponse.<List<LearningProgressDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(progress)
                .build());
    }

    @Operation(summary = "Cập nhật tiến độ học tập cá nhân", description = "Ghi nhận trạng thái hoàn thành hoặc thời gian tương tác với học liệu số.")
    @PutMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<LearningProgressDTO>> updateProgress(
            @Valid @RequestBody UpdateLearningProgressDTO updateDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        
        LearningProgressDTO updated = progressService.updateProgress(currentUserId, updateDTO);
        return ResponseEntity.ok(ApiResponse.<LearningProgressDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }
}