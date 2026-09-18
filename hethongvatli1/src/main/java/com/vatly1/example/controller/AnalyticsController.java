package com.vatly1.example.controller;

import com.vatly1.example.dto.AiTopicGapDTO;
import com.vatly1.example.dto.response.ApiResponse;
import com.vatly1.example.dto.MaterialEffectivenessDTO;
import com.vatly1.example.dto.QuestionQualityDTO;
import com.vatly1.example.dto.TopicDifficultyDTO;
import com.vatly1.example.dto.request.TriggerAggregationRequestDTO;
import com.vatly1.example.service.IAnalyticsAggregationService;
import com.vatly1.example.service.IAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & CTT", description = "APIs Phân tích học thuật CTT: Độ khó (p-value), Chỉ số phân biệt (DI), Cron Job tổng hợp")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final IAnalyticsService analyticsService;
    private final IAnalyticsAggregationService aggregationService;

    @Operation(summary = "Phân tích độ khó chương mục kiến thức", description = "Tính toán điểm trung bình, tỉ lệ sai sót và các phương án nhiễu phổ biến theo lý thuyết CTT.")
    @GetMapping("/topic-difficulty")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<TopicDifficultyDTO>>> getTopicDifficulty(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String period,
            HttpServletRequest request) {
        UUID requesterId = request.getAttribute("userId") != null
                ? UUID.fromString((String) request.getAttribute("userId")) : null;
        String role = (String) request.getAttribute("role");

        List<TopicDifficultyDTO> data = analyticsService.getTopicDifficulty(classId, subjectId, period, requesterId, role);
        return ResponseEntity.ok(ApiResponse.<List<TopicDifficultyDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Đánh giá chất lượng câu hỏi & chỉ số phân biệt (DI)", description = "Tính tỉ lệ đúng, chỉ số phân biệt Discrimination Index và nhãn chất lượng câu hỏi.")
    @GetMapping("/question-quality")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<QuestionQualityDTO>>> getQuestionQuality(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) Integer minUsed) {
        List<QuestionQualityDTO> data = analyticsService.getQuestionQuality(subjectId, topicId, minUsed);
        return ResponseEntity.ok(ApiResponse.<List<QuestionQualityDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Thống kê lỗ hổng kiến thức & từ chối của AI Socratic", description = "Tổng hợp các chủ đề sinh viên hay bị AI từ chối giải đáp hoặc hỏi nhiều nhất.")
    @GetMapping("/ai-gaps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AiTopicGapDTO>>> getAiGaps(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String period) {
        List<AiTopicGapDTO> data = analyticsService.getAiGaps(subjectId, period);
        return ResponseEntity.ok(ApiResponse.<List<AiTopicGapDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Đánh giá hiệu quả học liệu số", description = "Đo lường thời gian đọc, lượt xem và mức độ cải thiện điểm số tương quan.")
    @GetMapping("/material-effectiveness")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MaterialEffectivenessDTO>>> getMaterialEffectiveness(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) String period) {
        List<MaterialEffectivenessDTO> data = analyticsService.getMaterialEffectiveness(subjectId, topicId, period);
        return ResponseEntity.ok(ApiResponse.<List<MaterialEffectivenessDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Success")
                .data(data)
                .build());
    }

    @Operation(summary = "Kích hoạt thủ công tiến trình tổng hợp số liệu CTT (Chỉ Admin)", description = "Chạy tác vụ tính toán lại toàn bộ chỉ số phân tích học thuật.")
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> triggerAggregation(
            @RequestBody(required = false) TriggerAggregationRequestDTO request) {
        String period = request != null ? request.getPeriod() : null;
        aggregationService.triggerFullAggregation(period);
        return ResponseEntity.noContent().build();
    }
}
