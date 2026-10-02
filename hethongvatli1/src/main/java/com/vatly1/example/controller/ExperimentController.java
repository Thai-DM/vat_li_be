package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.request.ConfirmSubmissionDTO;
import com.vatly1.example.model.request.CreateExperimentAssignmentDTO;
import com.vatly1.example.model.request.CreateExperimentDTO;
import com.vatly1.example.model.dto.ExperimentAssignmentDTO;
import com.vatly1.example.model.dto.ExperimentDTO;
import com.vatly1.example.model.request.GradeSubmissionDTO;
import com.vatly1.example.model.request.SubmitExperimentDTO;
import com.vatly1.example.model.dto.SubmissionRubricDTO;
import com.vatly1.example.model.dto.SubmissionRubricSummaryDTO;
import com.vatly1.example.model.dto.ExperimentSubmissionDTO;
import com.vatly1.example.entity.ExperimentRubric;
import com.vatly1.example.entity.enums.SubmissionStatus;
import com.vatly1.example.service.IExperimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
@Tag(name = "Virtual Physics Lab", description = "APIs Quản lý 04 bài thí nghiệm ảo 3D Vật lý 1, tiêu chí Rubric đánh giá")
@SecurityRequirement(name = "bearerAuth")
public class ExperimentController {

    private final IExperimentService experimentService;

    @Operation(summary = "Lấy danh sách bài thí nghiệm", description = "Trả về danh sách các bài thí nghiệm ảo 3D. Có thể truyền subjectId để lọc theo môn học, hoặc bỏ trống để lấy toàn bộ.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExperimentDTO>>> getExperimentsBySubject(@RequestParam(required = false) UUID subjectId) {
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
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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

    @Operation(summary = "Lấy danh sách bài nộp thí nghiệm",
            description = "Dành cho Trợ giảng (TA), Giảng viên và Quản trị viên tra cứu danh sách bài nộp thí nghiệm. Hỗ trợ lọc theo assignmentId, experimentId, classId, studentId, status.")
    @GetMapping("/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExperimentSubmissionDTO>>> getSubmissions(
            @Parameter(description = "ID đợt giao bài thí nghiệm (tùy chọn)") @RequestParam(required = false) UUID assignmentId,
            @Parameter(description = "ID bài thí nghiệm (tùy chọn)") @RequestParam(required = false) UUID experimentId,
            @Parameter(description = "ID lớp học (tùy chọn)") @RequestParam(required = false) UUID classId,
            @Parameter(description = "ID sinh viên (tùy chọn)") @RequestParam(required = false) UUID studentId,
            @Parameter(description = "Trạng thái bài nộp (PENDING, GRADED, CONFIRMED,...) (tùy chọn)") @RequestParam(required = false) SubmissionStatus status,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        List<ExperimentSubmissionDTO> list = experimentService.getSubmissions(assignmentId, experimentId, classId, studentId, status, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<ExperimentSubmissionDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(list)
                .build());
    }

    @Operation(summary = "Lấy danh sách bài nộp theo đợt giao bài thí nghiệm",
            description = "Dành cho Trợ giảng (TA), Giảng viên lấy toàn bộ bài nộp của một đợt giao bài thí nghiệm.")
    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExperimentSubmissionDTO>>> getSubmissionsByAssignment(
            @Parameter(description = "ID đợt giao bài thí nghiệm") @PathVariable UUID assignmentId,
            @Parameter(description = "Trạng thái bài nộp (tùy chọn)") @RequestParam(required = false) SubmissionStatus status,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        List<ExperimentSubmissionDTO> list = experimentService.getSubmissions(assignmentId, null, null, null, status, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<ExperimentSubmissionDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(list)
                .build());
    }

    @Operation(summary = "Lấy chi tiết một bài nộp thí nghiệm theo ID",
            description = "Xem chi tiết bài nộp thí nghiệm, bao gồm thông tin sinh viên, đợt giao, dữ liệu nộp và toàn bộ tiêu chí Rubric kèm điểm chấm.")
    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<ExperimentSubmissionDTO>> getSubmissionById(
            @Parameter(description = "ID bài nộp thí nghiệm") @PathVariable UUID submissionId,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        ExperimentSubmissionDTO result = experimentService.getSubmissionById(submissionId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<ExperimentSubmissionDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(result)
                .build());
    }

    @Operation(summary = "Chấm điểm bài thí nghiệm theo tiêu chí Rubric", description = "Giảng viên / Trợ giảng chấm điểm từng tiêu chí Rubric cho bài nộp.")
    @PostMapping("/submissions/{submissionId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
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

    @Operation(summary = "Lấy danh sách tiêu chí Rubric và điểm số của bài nộp thí nghiệm",
            description = "Dành cho Giảng viên / Trợ giảng (TA) tra cứu tiêu chí Rubric và điểm số đã chấm của bài nộp. Hỗ trợ query param rubricId tùy chọn để lọc tiêu chí cụ thể.")
    @GetMapping({"/submissions/{submissionId}/rubrics", "/submissions/{submissionId}/scores"})
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<SubmissionRubricDTO>>> getSubmissionRubrics(
            @Parameter(description = "ID bài nộp thí nghiệm") @PathVariable UUID submissionId,
            @Parameter(description = "ID tiêu chí Rubric (tùy chọn)") @RequestParam(required = false) UUID rubricId,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        List<SubmissionRubricDTO> result = experimentService.getSubmissionRubrics(submissionId, rubricId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<SubmissionRubricDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(result)
                .build());
    }

    @Operation(summary = "Lấy chi tiết một tiêu chí Rubric cụ thể của bài nộp",
            description = "Dành cho Giảng viên / Trợ giảng (TA) tra cứu chi tiết một tiêu chí Rubric và điểm số tương ứng theo rubricId.")
    @GetMapping({"/submissions/{submissionId}/rubrics/{rubricId}", "/submissions/{submissionId}/scores/{rubricId}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<SubmissionRubricDTO>> getSubmissionRubricById(
            @Parameter(description = "ID bài nộp thí nghiệm") @PathVariable UUID submissionId,
            @Parameter(description = "ID tiêu chí Rubric") @PathVariable UUID rubricId,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        SubmissionRubricDTO result = experimentService.getSubmissionRubricById(submissionId, rubricId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<SubmissionRubricDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(result)
                .build());
    }

    @Operation(summary = "Lấy tổng hợp điểm Rubric của bài nộp thí nghiệm",
            description = "Dành cho Giảng viên / Trợ giảng (TA) xem tổng điểm đã chấm, tổng điểm tối đa và toàn bộ danh sách tiêu chí Rubric.")
    @GetMapping("/submissions/{submissionId}/rubric-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<SubmissionRubricSummaryDTO>> getSubmissionRubricSummary(
            @Parameter(description = "ID bài nộp thí nghiệm") @PathVariable UUID submissionId,
            HttpServletRequest request) {
        String attrId = (String) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        UUID currentUserId = attrId != null ? UUID.fromString(attrId) : null;
        SubmissionRubricSummaryDTO result = experimentService.getSubmissionRubricSummary(submissionId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<SubmissionRubricSummaryDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(result)
                .build());
    }

    @Operation(summary = "Lấy danh sách tiêu chí Rubric theo ID bài thí nghiệm",
            description = "Xem danh sách các tiêu chí Rubric đánh giá của một bài thí nghiệm ảo.")
    @GetMapping("/{experimentId}/rubrics")
    public ResponseEntity<ApiResponse<List<ExperimentRubric>>> getRubricsByExperimentId(
            @Parameter(description = "ID bài thí nghiệm") @PathVariable UUID experimentId) {
        List<ExperimentRubric> result = experimentService.getRubricsByExperimentId(experimentId);
        return ResponseEntity.ok(ApiResponse.<List<ExperimentRubric>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(result)
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