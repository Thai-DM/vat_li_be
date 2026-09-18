package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Dữ liệu chấm điểm bài nộp thí nghiệm ảo theo tiêu chí Rubric")
public class GradeSubmissionDTO {

    @Schema(description = "Định danh tiêu chí Rubric", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID rubricId;

    @Schema(description = "Điểm số chấm theo tiêu chí", example = "8.5")
    private BigDecimal score;

    @Schema(description = "Phản hồi / đánh giá của giảng viên hoặc trợ giảng", example = "Báo cáo thực hành tốt")
    private String feedback;

    @Schema(description = "Nhận xét chi tiết hoặc ghi chú bổ sung", example = "Đã kiểm tra số liệu đo đạc")
    private String comment;
}