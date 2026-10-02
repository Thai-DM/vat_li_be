package com.vatly1.example.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tiêu chí Rubric và điểm số chấm cho bài nộp thí nghiệm ảo")
public class SubmissionRubricDTO {

    @Schema(description = "Định danh tiêu chí Rubric", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID rubricId;

    @Schema(description = "Định danh bài thí nghiệm", example = "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
    private UUID experimentId;

    @Schema(description = "Tên tiêu chí chấm điểm Rubric", example = "Thao tác cài đặt & thu thập số liệu")
    private String criteriaName;

    @Schema(description = "Điểm tối đa của tiêu chí", example = "3.00")
    private BigDecimal maxScore;

    @Schema(description = "Mô tả chi tiết tiêu chí chấm điểm", example = "Thực hiện lắp ráp đúng mô hình và ghi chép số liệu đo")
    private String description;

    @Schema(description = "Định danh bài nộp thí nghiệm", example = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f")
    private UUID submissionId;

    @Schema(description = "Định danh bản ghi điểm số (null nếu chưa chấm)", example = "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a")
    private UUID scoreId;

    @Schema(description = "Điểm số thực tế đã chấm (null nếu chưa chấm)", example = "2.50")
    private BigDecimal score;

    @Schema(description = "Nhận xét của người chấm", example = "Số liệu đo khá chính xác, đồ thị rõ ràng")
    private String comment;

    @Schema(description = "Phản hồi của người chấm (đồng bộ với comment)", example = "Số liệu đo khá chính xác, đồ thị rõ ràng")
    private String feedback;

    @Schema(description = "Định danh người chấm (giảng viên / trợ giảng)")
    private UUID graderId;

    @Schema(description = "Thời điểm chấm điểm")
    private Instant gradedAt;

    @Schema(description = "Trạng thái tiêu chí này đã được chấm điểm hay chưa", example = "true")
    private Boolean isGraded;
}
