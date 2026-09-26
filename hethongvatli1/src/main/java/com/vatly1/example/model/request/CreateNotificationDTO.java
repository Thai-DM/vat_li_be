package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationDTO {

    @NotBlank(message = "Tiêu đề thông báo không được để trống")
    @Schema(description = "Tiêu đề thông báo", example = "Thông báo kiểm tra 15 phút tuần tới")
    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống")
    @Schema(description = "Nội dung chi tiết thông báo", example = "Các em chú ý ôn tập kỹ chương 2 Động lực học chất điểm trước buổi học Thứ Năm tới.")
    private String content;

    @Schema(description = "Loại thông báo (ANNOUNCEMENT, EXAM_NEW, SYSTEM,...)", example = "ANNOUNCEMENT")
    private NotificationType type;

    @Schema(description = "ID đối tượng liên quan (nếu có)", example = "cccccccc-cccc-cccc-cccc-cccccccccccc")
    private UUID referenceId;

    @Schema(description = "Loại đối tượng liên quan (CLASS, EXAM, EXPERIMENT,...)", example = "CLASS")
    private String referenceType;
}
