package com.vatly1.example.controller;

import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.request.RejectQuestionDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.dto.QuestionApprovalDTO;
import com.vatly1.example.model.response.QuestionImportResultDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.service.IQuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
@Tag(name = "Question Bank", description = "APIs Ngân hàng câu hỏi trắc nghiệm, phân loại Bloom, tạo câu hỏi tự động từ Excel")
@SecurityRequirement(name = "bearerAuth")
public class QuestionBankController {

    private final IQuestionBankService questionBankService;

    @Operation(summary = "Lấy danh sách câu hỏi theo bộ lọc", description = "Tìm kiếm câu hỏi theo môn học (tùy chọn), chương, bài học và độ khó Bloom. Không bắt buộc truyền môn học; nếu bỏ trống sẽ lấy tất cả câu hỏi.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Page<QuestionBankDTO>>> getQuestions(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID chapterId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) DifficultyLevel difficultyLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QuestionBankDTO> questions = questionBankService.getQuestions(subjectId, chapterId, topicId, difficultyLevel, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<QuestionBankDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(questions)
                .build());
    }

    @Operation(summary = "Lấy chi tiết câu hỏi theo ID", description = "Xem nội dung câu hỏi, danh sách đáp án A/B/C/D và giải thích chi tiết.")
    @GetMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> getQuestionById(@PathVariable UUID questionId) {
        QuestionBankDTO question = questionBankService.getQuestionById(questionId);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(question)
                .build());
    }

    @Operation(summary = "Tạo câu hỏi trắc nghiệm mới", description = "Thêm câu hỏi mới vào ngân hàng (cần phê duyệt trước khi đưa vào đề thi).")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> createQuestion(
            @Valid @RequestBody CreateQuestionDTO createDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        QuestionBankDTO created = questionBankService.createQuestion(createDTO, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Success")
                .data(created)
                .build());
    }

    @Operation(summary = "Cập nhật nội dung câu hỏi", description = "Chỉnh sửa câu hỏi, đáp án hoặc giải thích chi tiết.")
    @PutMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody CreateQuestionDTO updateDTO,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        QuestionBankDTO updated = questionBankService.updateQuestion(questionId, updateDTO, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(updated)
                .build());
    }

    @Operation(summary = "Phê duyệt câu hỏi vào ngân hàng chính thức", description = "Chuyển trạng thái câu hỏi thành APPROVED để sẵn sàng sinh đề.")
    @PutMapping("/{questionId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> approveQuestion(
            @PathVariable UUID questionId,
            @RequestParam(required = false) String comment,
            HttpServletRequest request) {
        UUID currentUserId = request.getAttribute("userId") != null ? UUID.fromString(request.getAttribute("userId").toString()) : null;
        String role = request.getAttribute("role") != null ? request.getAttribute("role").toString() : null;
        QuestionBankDTO approved = questionBankService.approveQuestion(questionId, currentUserId, role, comment);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Question approved successfully")
                .data(approved)
                .build());
    }

    @Operation(summary = "Từ chối duyệt câu hỏi", description = "Admin từ chối câu hỏi với lý do bắt buộc, đưa câu hỏi về trạng thái REJECTED.")
    @PutMapping("/{questionId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> rejectQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody RejectQuestionDTO rejectDTO,
            HttpServletRequest request) {
        UUID currentUserId = request.getAttribute("userId") != null ? UUID.fromString(request.getAttribute("userId").toString()) : null;
        String role = request.getAttribute("role") != null ? request.getAttribute("role").toString() : null;
        QuestionBankDTO rejected = questionBankService.rejectQuestion(questionId, rejectDTO.getRejectionReason(), currentUserId, role, rejectDTO.getComment());
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Question rejected successfully")
                .data(rejected)
                .build());
    }

    @Operation(summary = "Gửi yêu cầu duyệt câu hỏi", description = "Giảng viên chuyển câu hỏi từ DRAFT hoặc REJECTED sang PENDING để chờ Admin phê duyệt.")
    @PutMapping("/{questionId}/submit-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> submitQuestionForReview(
            @PathVariable UUID questionId,
            @RequestParam(required = false) String comment,
            HttpServletRequest request) {
        UUID currentUserId = request.getAttribute("userId") != null ? UUID.fromString(request.getAttribute("userId").toString()) : null;
        String role = request.getAttribute("role") != null ? request.getAttribute("role").toString() : null;
        QuestionBankDTO submitted = questionBankService.submitQuestionForReview(questionId, currentUserId, role, comment);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Question submitted for review successfully")
                .data(submitted)
                .build());
    }

    @Operation(summary = "Lấy lịch sử phê duyệt câu hỏi", description = "Xem nhật ký kiểm toán toàn bộ các bước phê duyệt / từ chối / gửi duyệt của câu hỏi.")
    @GetMapping("/{questionId}/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<QuestionApprovalDTO>>> getApprovalHistory(
            @PathVariable UUID questionId,
            HttpServletRequest request) {
        UUID currentUserId = request.getAttribute("userId") != null ? UUID.fromString(request.getAttribute("userId").toString()) : null;
        String role = request.getAttribute("role") != null ? request.getAttribute("role").toString() : null;
        List<QuestionApprovalDTO> history = questionBankService.getQuestionApprovalHistory(questionId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<QuestionApprovalDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(history)
                .build());
    }

    @Operation(summary = "Gán danh sách CLO cho câu hỏi", description = "Liên kết câu hỏi với các chuẩn đầu ra (CLO) trong cùng môn học.")
    @PutMapping("/{questionId}/clos")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionBankDTO>> assignClos(
            @PathVariable UUID questionId,
            @RequestBody List<UUID> cloIds,
            HttpServletRequest request) {
        UUID currentUserId = request.getAttribute("userId") != null ? UUID.fromString(request.getAttribute("userId").toString()) : null;
        String role = request.getAttribute("role") != null ? request.getAttribute("role").toString() : null;
        QuestionBankDTO updated = questionBankService.assignClosToQuestion(questionId, cloIds, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("CLOs assigned successfully")
                .data(updated)
                .build());
    }

    @Operation(summary = "Xóa câu hỏi khỏi ngân hàng", description = "Xóa câu hỏi khỏi ngân hàng câu hỏi.")
    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable UUID questionId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        questionBankService.deleteQuestion(questionId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(null)
                .build());
    }

    @Operation(summary = "Tải file mẫu Excel nhập câu hỏi", description = "Tải xuống file Excel (.xlsx) chuẩn hóa để giáo viên điền danh sách câu hỏi.")
    @GetMapping("/import-excel/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] excelBytes = questionBankService.downloadQuestionExcelTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mau_import_cau_hoi.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @Operation(summary = "Tạo câu hỏi tự động từ file Excel", description = "Bóc tách tệp bảng tính Excel (.xlsx, .xls) và nhập hàng loạt câu hỏi trắc nghiệm vào ngân hàng.")
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionImportResultDTO>> importQuestionsFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") UUID subjectId,
            @RequestParam("topicId") UUID topicId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        QuestionImportResultDTO result = questionBankService.importQuestionsFromExcel(file, subjectId, topicId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<QuestionImportResultDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Parsed and imported questions from Excel successfully")
                .data(result)
                .build());
    }
}