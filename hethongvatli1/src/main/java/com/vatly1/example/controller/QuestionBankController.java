package com.vatly1.example.controller;

import com.vatly1.example.dto.ApiResponse;
import com.vatly1.example.dto.CreateQuestionDTO;
import com.vatly1.example.dto.QuestionBankDTO;
import com.vatly1.example.dto.QuestionImportResultDTO;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.service.IQuestionBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
@Tag(name = "Question Bank", description = "APIs Ngân hàng câu hỏi trắc nghiệm, phân loại Bloom, nhập câu hỏi từ PDF")
@SecurityRequirement(name = "bearerAuth")
public class QuestionBankController {

    private final IQuestionBankService questionBankService;

    @Operation(summary = "Lấy danh sách câu hỏi theo bộ lọc", description = "Tìm kiếm câu hỏi theo môn học, chương mục và độ khó Bloom.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Page<QuestionBankDTO>>> getQuestions(
            @RequestParam UUID subjectId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) DifficultyLevel difficultyLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QuestionBankDTO> questions = questionBankService.getQuestions(subjectId, topicId, difficultyLevel, pageable);
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
    public ResponseEntity<ApiResponse<QuestionBankDTO>> approveQuestion(@PathVariable UUID questionId) {
        QuestionBankDTO approved = questionBankService.approveQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.<QuestionBankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(approved)
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

    @Operation(summary = "Nhập tự động câu hỏi từ file PDF (AI OCR)", description = "Phân tích cú pháp tệp đề thi PDF và trích xuất hàng loạt câu hỏi trắc nghiệm.")
    @PostMapping(value = "/import-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuestionImportResultDTO>> importQuestionsFromPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") UUID subjectId,
            @RequestParam("topicId") UUID topicId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        QuestionImportResultDTO result = questionBankService.importQuestionsFromPdf(file, subjectId, topicId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<QuestionImportResultDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Parsed and imported questions from PDF successfully")
                .data(result)
                .build());
    }
}
