package com.vatly1.example.controller;

import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.LearningProgressDTO;
import com.vatly1.example.service.ILearningProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classes/{classId}/progress")
@RequiredArgsConstructor
@Tag(name = "Learning Progress", description = "APIs Theo dõi tiến độ học tập và hoàn thành học liệu của sinh viên")
@SecurityRequirement(name = "bearerAuth")
public class LearningProgressController {

    private final ILearningProgressService progressService;

    @Operation(summary = "Lấy tiến độ hoàn thành học liệu của cả lớp", description = "Giảng viên theo dõi tỉ lệ xem video, đọc tài liệu của từng sinh viên trong lớp.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<LearningProgressDTO>>> getProgressByClass(
            @PathVariable UUID classId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        
        List<LearningProgressDTO> progress = progressService.getProgressByClass(classId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<LearningProgressDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(progress)
                .build());
    }
}
