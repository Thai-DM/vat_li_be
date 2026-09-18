package com.vatly1.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Dữ liệu xác nhận và chốt điểm chính thức bài nộp thí nghiệm ảo")
public class ConfirmSubmissionDTO {

    @Schema(description = "Ghi chú phê duyệt / nhận xét chung cuộc của Giảng viên", example = "Xác nhận điểm số chung cuộc")
    private String note;
}
