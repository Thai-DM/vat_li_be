package com.vatly1.example.controller;

import com.vatly1.example.dto.request.AddExamQuestionDTO;
import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.request.CreateExamDTO;
import com.vatly1.example.dto.dto.ExamAttemptDTO;
import com.vatly1.example.dto.dto.ExamDTO;
import com.vatly1.example.dto.request.SubmitAnswerDTO;
import com.vatly1.example.service.IExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Tag(name = "Exam Management", description = "APIs Cấu hình kỳ thi, sinh ma trận đề, làm bài, nộp bài, kiểm soát số lượt (Multi-attempt)")
@SecurityRequirement(name = "bearerAuth")
public class ExamController {

    private final IExamService examService;

    @Operation(summary = "Tạo kỳ thi mới", description = "Tạo kỳ thi với cấu hình thời gian, số câu, ma trận đề và hình thức thi.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamDTO>> createExam(
            @Valid @RequestBody CreateExamDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamDTO created = examService.createExam(dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Exam created successfully")
                .data(created)
                .build());
    }

    @Operation(summary = "Thêm câu hỏi thủ công vào đề thi", description = "Chỉ định trực tiếp câu hỏi từ ngân hàng vào kỳ thi.")
    @PostMapping("/{examId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> addQuestionToExam(
            @PathVariable UUID examId,
            @Valid @RequestBody AddExamQuestionDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        examService.addQuestionToExam(examId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Question added to exam successfully")
                .data(null)
                .build());
    }

    @Operation(summary = "Tự động sinh đề thi ngẫu nhiên theo ma trận", description = "Lấy câu hỏi ngẫu nhiên từ ngân hàng theo tỉ lệ chương mục và mức độ Bloom.")
    @PostMapping("/{examId}/generate-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoGenerateQuestions(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        int added = examService.autoGenerateQuestions(examId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .status(HttpStatus.OK.value())
                .message("Questions generated successfully")
                .data(Map.of("questionsAdded", added))
                .build());
    }

    @Operation(summary = "Lấy danh sách kỳ thi của lớp học", description = "Trả về tất cả kỳ thi trắc nghiệm thuộc một lớp học.")
    @GetMapping("/class/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamDTO>>> getExamsByClass(
            @PathVariable UUID classId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<ExamDTO> exams = examService.getExamsByClass(classId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<ExamDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(exams)
                .build());
    }

    @Operation(summary = "Lấy chi tiết kỳ thi theo ID", description = "Xem cấu hình chi tiết, thời gian và thông tin kỳ thi.")
    @GetMapping("/{examId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'TA', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamDTO>> getExamById(@PathVariable UUID examId) {
        ExamDTO exam = examService.getExamById(examId);
        return ResponseEntity.ok(ApiResponse.<ExamDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(exam)
                .build());
    }

    @Operation(summary = "Sinh viên bắt đầu làm bài thi (Tạo lượt thi)", description = "Khởi tạo lượt làm bài mới, hỗ trợ multi-attempt cho đề luyện tập.")
    @PostMapping("/{examId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ExamAttemptDTO>> startAttempt(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamAttemptDTO attempt = examService.startAttempt(examId, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamAttemptDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Attempt started")
                .data(attempt)
                .build());
    }

    @Operation(summary = "Lưu câu trả lời tạm thời của sinh viên", description = "Ghi nhận phương án chọn cho từng câu hỏi trong quá trình làm bài.")
    @PostMapping("/attempts/{attemptId}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> submitAnswer(
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmitAnswerDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        examService.submitAnswer(attemptId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Answer saved")
                .data(null)
                .build());
    }

    @Operation(summary = "Nộp bài thi và chấm điểm tự động", description = "Khóa bài thi bằng khóa bi quan (SELECT FOR UPDATE) chống race condition và tính điểm.")
    @PutMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ExamAttemptDTO>> submitAttempt(
            @PathVariable UUID attemptId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamAttemptDTO graded = examService.submitAttempt(attemptId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Exam submitted and graded successfully")
                .data(graded)
                .build());
    }

    @Operation(summary = "Lấy lượt làm bài gần nhất của sinh viên hiện tại", description = "Xem thông tin hoặc tiếp tục bài thi đang làm dở.")
    @GetMapping("/{examId}/my-attempt")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ExamAttemptDTO>> getMyAttempt(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamAttemptDTO attempt = examService.getMyAttempt(examId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(attempt)
                .build());
    }

    @Operation(summary = "Lấy toàn bộ lịch sử các lượt làm bài của sinh viên cho kỳ thi", description = "Xem danh sách và điểm số tất cả các lần thi (đặc biệt cho đề thi luyện tập PRACTICE).")
    @GetMapping("/{examId}/my-attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamAttemptDTO>>> getMyAttempts(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<ExamAttemptDTO> attempts = examService.getMyAttempts(examId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<ExamAttemptDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(attempts)
                .build());
    }

    @Operation(summary = "Xem chi tiết kết quả lượt thi", description = "Xem bảng điểm, số câu đúng/sai và phân tích chi tiết bài thi đã nộp.")
    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<ExamAttemptDTO>> getAttemptDetail(
            @PathVariable UUID attemptId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        ExamAttemptDTO attempt = examService.getAttemptDetail(attemptId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(attempt)
                .build());
    }
}