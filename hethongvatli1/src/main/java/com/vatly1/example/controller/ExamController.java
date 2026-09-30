package com.vatly1.example.controller;

import com.vatly1.example.model.dto.BatchSaveResultDTO;
import com.vatly1.example.model.dto.ExamAttemptDTO;
import com.vatly1.example.model.dto.ExamAttemptPolicyDTO;
import com.vatly1.example.model.dto.ExamAttemptProgressDTO;
import com.vatly1.example.model.dto.ExamAttemptSummaryDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamParticipantDTO;
import com.vatly1.example.model.dto.ExamQuestionDetailDTO;
import com.vatly1.example.model.dto.ExamRosterDTO;
import com.vatly1.example.model.dto.StudentExamQuestionDTO;
import com.vatly1.example.model.request.BatchSubmitAnswerDTO;
import com.vatly1.example.model.request.AddExamQuestionDTO;
import com.vatly1.example.model.request.CreateExamDTO;
import com.vatly1.example.model.request.GradeAttemptDTO;
import com.vatly1.example.model.request.SubmitAnswerDTO;
import com.vatly1.example.model.request.TransferStudentDTO;
import com.vatly1.example.model.request.UpdateExamDTO;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.service.IExamParticipantService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final IExamParticipantService examParticipantService;

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

    @Operation(summary = "Lấy danh sách tất cả kỳ thi", description = "Lấy danh sách kỳ thi, có thể lọc theo lớp (classId) hoặc lấy tất cả kỳ thi mà người dùng có quyền xem.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamDTO>>> getExams(
            @RequestParam(required = false) UUID classId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<ExamDTO> exams;
        if (classId != null) {
            exams = examService.getExamsByClass(classId, currentUserId, role);
        } else {
            exams = examService.getAllExams(currentUserId, role);
        }
        return ResponseEntity.ok(ApiResponse.<List<ExamDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(exams)
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
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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
    @RequestMapping(value = "/attempts/{attemptId}/submit", method = {org.springframework.web.bind.annotation.RequestMethod.PUT, org.springframework.web.bind.annotation.RequestMethod.POST})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
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

    @Operation(summary = "Lấy danh sách câu hỏi theo lượt thi", description = "Lấy danh sách câu hỏi và các lựa chọn cho sinh viên làm bài. Ẩn đáp án đúng trong lúc làm bài và lưu lại lựa chọn đã chọn.")
    @GetMapping("/attempts/{attemptId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<StudentExamQuestionDTO>>> getAttemptQuestions(
            @PathVariable UUID attemptId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<StudentExamQuestionDTO> questions = examService.getAttemptQuestions(attemptId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<StudentExamQuestionDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(questions)
                .build());
    }

    @Operation(summary = "Tiến độ làm bài thi", description = "Trả về số câu đã trả lời, tổng số câu, thời gian còn lại (giây) và trạng thái lượt làm.")
    @GetMapping("/attempts/{attemptId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<ExamAttemptProgressDTO>> getAttemptProgress(
            @PathVariable UUID attemptId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        ExamAttemptProgressDTO progress = examService.getAttemptProgress(attemptId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptProgressDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(progress)
                .build());
    }

    @Operation(summary = "Lưu nháp câu trả lời theo lô (Autosave batch)", description = "Lưu nháp hàng loạt câu trả lời một lần để tối ưu mạng và chống mất dữ liệu.")
    @PostMapping("/attempts/{attemptId}/autosave")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<BatchSaveResultDTO>> autosaveAnswers(
            @PathVariable UUID attemptId,
            @Valid @RequestBody BatchSubmitAnswerDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        BatchSaveResultDTO result = examService.autosaveAnswers(attemptId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<BatchSaveResultDTO>builder()
                .status(HttpStatus.OK.value())
                .message(result.getMessage())
                .data(result)
                .build());
    }

    @Operation(summary = "Chính sách lượt thi & điều kiện làm bài", description = "Kiểm tra số lượt tối đa, số lượt đã dùng, có lượt dở dang hay không và thời điểm được thi.")
    @GetMapping("/{examId}/attempt-policy")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<ExamAttemptPolicyDTO>> getExamAttemptPolicy(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamAttemptPolicyDTO policy = examService.getExamAttemptPolicy(examId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptPolicyDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(policy)
                .build());
    }

    @Operation(summary = "Thêm sinh viên thi ghép vào ca thi", description = "Chuyển sinh viên từ lớp khác cùng môn học sang làm bài trong ca thi này.")
    @PostMapping("/{examId}/transfers")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamParticipantDTO>> transferStudentToExam(
            @PathVariable UUID examId,
            @Valid @RequestBody TransferStudentDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        ExamParticipantDTO result = examParticipantService.transferStudentToExam(examId, dto, currentUserId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamParticipantDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Chuyển sinh viên sang ca thi thành công")
                .data(result)
                .build());
    }

    @Operation(summary = "Hủy sinh viên thi ghép khỏi ca thi", description = "Xóa sinh viên khỏi danh sách thi ghép của ca thi này.")
    @DeleteMapping("/{examId}/transfers/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> removeTransferredStudent(
            @PathVariable UUID examId,
            @PathVariable UUID studentId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        examParticipantService.removeTransferredStudent(examId, studentId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã hủy quyền thi ghép của sinh viên tại ca thi này")
                .build());
    }

    @Operation(summary = "Danh sách thí sinh đầy đủ của ca thi", description = "Xem toàn bộ thí sinh chính thức của lớp và thí sinh thi ghép.")
    @GetMapping("/{examId}/roster")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<ExamRosterDTO>> getExamRoster(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        ExamRosterDTO roster = examParticipantService.getExamRoster(examId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<ExamRosterDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(roster)
                .build());
    }

    @Operation(summary = "Xem các ca thi thi ghép của tôi", description = "Sinh viên xem danh sách các bài thi mà mình được cấp quyền thi ghép từ lớp khác cùng môn.")
    @GetMapping("/my-transferred-exams")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ExamDTO>>> getMyTransferredExams(HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<ExamDTO> exams = examParticipantService.getTransferredExamsForStudent(currentUserId);
        return ResponseEntity.ok(ApiResponse.<List<ExamDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(exams)
                .build());
    }

    @Operation(summary = "Xem câu hỏi đã có trong đề thi", description = "Lấy danh sách các câu hỏi cùng các phương án lựa chọn trong đề thi.")
    @GetMapping("/{examId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamQuestionDetailDTO>>> getExamQuestions(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<ExamQuestionDetailDTO> questions = examService.getExamQuestions(examId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<ExamQuestionDetailDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(questions)
                .build());
    }

    @Operation(summary = "Gỡ câu hỏi khỏi đề thi", description = "Xóa câu hỏi khỏi đề thi khi chưa có sinh viên làm bài.")
    @DeleteMapping("/{examId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> removeQuestionFromExam(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        examService.removeQuestionFromExam(examId, questionId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Question removed from exam successfully")
                .build());
    }

    @Operation(summary = "Cập nhật cấu hình đề thi", description = "Sửa tên đề, thời gian làm bài, thời gian mở/đóng, hình thức thi.")
    @PutMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamDTO>> updateExam(
            @PathVariable UUID examId,
            @RequestBody UpdateExamDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamDTO updated = examService.updateExam(examId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<ExamDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Exam updated successfully")
                .data(updated)
                .build());
    }

    @Operation(summary = "Xóa đề thi chưa mở", description = "Xóa kỳ thi khi chưa có sinh viên nào thực hiện làm bài.")
    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteExam(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        examService.deleteExam(examId, currentUserId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Exam deleted successfully")
                .build());
    }

    @Operation(summary = "Xem danh sách lượt làm bài của đề thi", description = "Giảng viên xem danh sách các lượt làm bài, trạng thái và kết quả điểm thi.")
    @GetMapping("/{examId}/attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<List<ExamAttemptSummaryDTO>>> getExamAttempts(
            @PathVariable UUID examId,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        String role = (String) request.getAttribute("role");
        List<ExamAttemptSummaryDTO> attempts = examService.getExamAttempts(examId, currentUserId, role);
        return ResponseEntity.ok(ApiResponse.<List<ExamAttemptSummaryDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(attempts)
                .build());
    }

    @Operation(summary = "Chấm thủ công hoặc điều chỉnh điểm lượt làm bài", description = "Giảng viên chấm câu tự luận hoặc điều chỉnh điểm tổng kết của lượt thi.")
    @PutMapping("/attempts/{attemptId}/grade")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamAttemptDTO>> gradeAttempt(
            @PathVariable UUID attemptId,
            @Valid @RequestBody GradeAttemptDTO dto,
            HttpServletRequest request) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        ExamAttemptDTO updated = examService.gradeAttempt(attemptId, dto, currentUserId);
        return ResponseEntity.ok(ApiResponse.<ExamAttemptDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Attempt graded successfully")
                .data(updated)
                .build());
    }
}