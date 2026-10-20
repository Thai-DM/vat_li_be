package com.vatly1.example.controller;

import com.vatly1.example.model.dto.LearningProgressDTO;
import com.vatly1.example.model.dto.MaterialProgressDTO;
import com.vatly1.example.model.request.UpdateLearningProgressDTO;
import com.vatly1.example.model.request.UpdateMaterialProgressDTO;
import com.vatly1.example.model.response.ApiResponse;
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

    @Operation(summary = "Lấy tiến độ học tập của tôi", description = "Sinh viên xem danh sách học liệu đã hoàn thành và tiến độ phần trăm. Có thể truyền classId để lọc theo lớp, hoặc bỏ trống để lấy toàn bộ tiến độ của các lớp đã tham gia.")
    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LearningProgressDTO>>> getMyProgress(
            @RequestParam(required = false) UUID classId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        
        List<LearningProgressDTO> progress = progressService.getMyProgress(currentUserId, classId);
        return ResponseEntity.ok(ApiResponse.<List<LearningProgressDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(progress)
                .build());
    }

    @Operation(summary = "Cập nhật tiến độ học tập cá nhân", description = "Ghi nhận trạng thái hoàn thành hoặc thời gian tương tác với học liệu số hoặc chủ đề.")
    @PutMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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

    @Operation(summary = "Lấy chi tiết tiến độ từng học liệu", description = "Xem danh sách trạng thái (đã học, đang học, thời gian tương tác) của từng học liệu.")
    @GetMapping("/materials")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MaterialProgressDTO>>> getMyMaterialProgress(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID topicId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));

        List<MaterialProgressDTO> list = progressService.getMyMaterialProgress(currentUserId, classId, topicId);
        return ResponseEntity.ok(ApiResponse.<List<MaterialProgressDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(list)
                .build());
    }

    @Operation(summary = "Cập nhật tiến độ của học liệu cụ thể", description = "Ghi nhận tiến độ % và thời gian học cho một học liệu số, tự động đồng bộ lên tiến độ Topic.")
    @PutMapping("/materials")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<MaterialProgressDTO>> updateMaterialProgress(
            @Valid @RequestBody UpdateMaterialProgressDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));

        MaterialProgressDTO updated = progressService.updateMaterialProgress(currentUserId, dto);
        return ResponseEntity.ok(ApiResponse.<MaterialProgressDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }

    @Operation(summary = "Cập nhật tiến độ của học liệu qua đường dẫn", description = "Ghi nhận trạng thái hoàn thành theo materialId trên đường dẫn.")
    @PutMapping("/materials/{materialId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<MaterialProgressDTO>> updateMaterialProgressById(
            @PathVariable UUID materialId,
            @Valid @RequestBody UpdateMaterialProgressDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        dto.setMaterialId(materialId);

        MaterialProgressDTO updated = progressService.updateMaterialProgress(currentUserId, dto);
        return ResponseEntity.ok(ApiResponse.<MaterialProgressDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }
}