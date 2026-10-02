package com.vatly1.example.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tổng hợp danh sách Rubric và kết quả chấm điểm của bài nộp thí nghiệm ảo")
public class SubmissionRubricSummaryDTO {

    @Schema(description = "Định danh bài nộp thí nghiệm", example = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f")
    private UUID submissionId;

    @Schema(description = "Định danh bài thí nghiệm", example = "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
    private UUID experimentId;

    @Schema(description = "Tiêu đề bài thí nghiệm", example = "Bài 1: Khảo sát chuyển động rơi tự do")
    private String experimentTitle;

    @Schema(description = "Định danh sinh viên nộp bài")
    private UUID studentId;

    @Schema(description = "Trạng thái bài nộp (PENDING, GRADED, CONFIRMED)", example = "GRADED")
    private String status;

    @Schema(description = "Tổng điểm đã chấm trên các tiêu chí", example = "8.50")
    private BigDecimal totalScore;

    @Schema(description = "Tổng điểm tối đa của toàn bộ Rubric", example = "10.00")
    private BigDecimal totalMaxScore;

    @Schema(description = "Danh sách chi tiết từng tiêu chí Rubric và điểm số tương ứng")
    private List<SubmissionRubricDTO> rubrics;
}
