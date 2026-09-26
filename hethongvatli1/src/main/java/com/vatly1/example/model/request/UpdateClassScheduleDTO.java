package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.LessonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateClassScheduleDTO {

    @Min(value = 2, message = "Thứ trong tuần hợp lệ từ 2 (Thứ Hai) đến 8 (Chủ Nhật)")
    @Max(value = 8, message = "Thứ trong tuần hợp lệ từ 2 (Thứ Hai) đến 8 (Chủ Nhật)")
    @Schema(description = "Thứ trong tuần: 2 (Thứ Hai), 3 (Thứ Ba), ..., 7 (Thứ Bảy), 8 (Chủ Nhật)", example = "2")
    private Integer dayOfWeek;

    @Schema(description = "Tiết bắt đầu", example = "1")
    private Integer startPeriod;

    @Schema(description = "Tiết kết thúc", example = "3")
    private Integer endPeriod;

    @Schema(description = "Giờ bắt đầu (HH:mm:ss)", example = "07:00:00")
    private LocalTime startTime;

    @Schema(description = "Giờ kết thúc (HH:mm:ss)", example = "09:30:00")
    private LocalTime endTime;

    @Schema(description = "Phòng học", example = "A1-203")
    private String room;

    @Schema(description = "Tòa nhà / Cơ sở", example = "Nhà A1")
    private String building;

    @Schema(description = "Loại buổi học (THEORY, LAB, EXERCISE, EXAM)", example = "LAB")
    private LessonType lessonType;

    @Schema(description = "Ghi chú thêm", example = "Thực hành bài số 3")
    private String notes;
}
