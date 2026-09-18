package com.vatly1.example.controller;

import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.request.ConfirmSubmissionDTO;
import com.vatly1.example.dto.request.CreateExperimentAssignmentDTO;
import com.vatly1.example.dto.request.CreateExperimentDTO;
import com.vatly1.example.dto.dto.ExperimentAssignmentDTO;
import com.vatly1.example.dto.dto.ExperimentDTO;
import com.vatly1.example.dto.request.GradeSubmissionDTO;
import com.vatly1.example.dto.request.SubmitExperimentDTO;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.service.IExperimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
@Tag(name = "Virtual Physics Lab", description = "APIs Quản lý 04 bài thí nghiệm ảo 3D Vật lý 1, tiêu chí Rubric đánh giá")
@SecurityRequirement(name = "bearerAuth")
public class ExperimentController {

    private final IExperimentService experimentService;

    @Operation(summary = "Lấy danh sách bài thí nghiệm theo môn học", description = "Trả về danh sách các bài thí nghiệm ảo 3D thuộc môn học.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExperimentDTO>>> getExperimentsBySubject(@RequestParam UUID subjectId) {
        List<ExperimentDTO> experiments = experimentService.getExperimentsBySubject(subjectId);
        return ResponseEntity.ok(ApiResponse.<List<ExperimentDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(experiments)
                .build());
    }

    @Operation(summary = "Lấy chi tiết bài thí nghiệm theo ID", description = "Xem cấu hình, tiêu chí đánh giá và thông số của bài thí nghiệm.")
    @GetMapping("/{experimentId}")
    public ResponseEntity<ApiResponse<ExperimentDTO>> getExperimentById(@PathVariable UUID experimentId) {
        ExperimentDTO experiment = experimentService.getExperimentById(experimentId);
        return ResponseEntity.ok(ApiResponse.<ExperimentDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(experiment)
                .build());
    }

    @Operation(summary = "Tạo bài thí nghiệm ảo mới", description = "Thêm bài thí nghiệm ảo mới vào hệ thống.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExperimentDTO>> createExperiment(
            @Valid @RequestBody CreateExperimentDTO createDTO) {
        ExperimentDTO created = experimentService.createExperiment(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExperimentDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(created)
                .build());
    }

    @Operation(summary = "Giao bài thí nghiệm cho lớp học", description = "Tạo đợt thực hành thí nghiệm ảo cho lớp với hạn nộp.")
    @PostMapping("/{experimentId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExperimentAssignmentDTO>> assignExperiment(
            @PathVariable UUID experimentId,
            @Valid @RequestBody CreateExperimentAssignmentDTO assignDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExperimentAssignmentDTO assignment = experimentService.assignExperiment(experimentId, assignDTO, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExperimentAssignmentDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(assignment)
                .build());
    }

    @Operation(summary = "Sinh viên nộp kết quả thí nghiệm ảo", description = "Tải lên file số liệu đo đạc, đồ thị và hình ảnh minh chứng.")
    @PostMapping(value = "/assignments/{assignmentId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> submitExperiment(
            @PathVariable UUID assignmentId,
            @Valid @ModelAttribute SubmitExperimentDTO submitDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        experimentService.submitExperiment(assignmentId, submitDTO, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(null)
                .build());
    }

    @Operation(summary = "Chấm điểm bài thí nghiệm theo tiêu chí Rubric", description = "Giảng viên / Trợ giảng chấm điểm từng tiêu chí Rubric cho bài nộp.")
    @PostMapping("/submissions/{submissionId}/scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<Void>> gradeSubmission(
            @PathVariable UUID submissionId,
            @Valid @RequestBody(required = false) GradeSubmissionDTO scoreDTO,
            HttpServletRequest request) {
        String attr = (String) request.getAttribute("userId");
        UUID currentUserId = attr != null ? UUID.fromString(attr) : null;
        experimentService.gradeSubmission(submissionId, scoreDTO, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Score saved successfully")
                .data(null)
                .build());
    }

    @Operation(summary = "Xác nhận kết quả thí nghiệm cuối cùng", description = "Giảng viên phê duyệt và chốt điểm chính thức cho sinh viên.")
    @PostMapping("/submissions/{submissionId}/confirmation")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> confirmSubmission(
            @PathVariable UUID submissionId,
            @Valid @RequestBody(required = false) ConfirmSubmissionDTO confirmDTO,
            HttpServletRequest request) {
        String attr = (String) request.getAttribute("userId");
        UUID currentUserId = attr != null ? UUID.fromString(attr) : null;
        String note = (confirmDTO != null) ? confirmDTO.getNote() : null;
        experimentService.confirmSubmission(submissionId, note, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Confirmed successfully")
                .data(null)
                .build());
    }
}