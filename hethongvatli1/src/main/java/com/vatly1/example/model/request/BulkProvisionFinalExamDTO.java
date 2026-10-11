package com.vatly1.example.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProvisionFinalExamDTO {

    @NotNull(message = "matrixId không được để trống")
    private UUID matrixId;

    @NotEmpty(message = "Danh sách mã lớp (classIds) không được để trống")
    private List<UUID> classIds;

    @NotNull(message = "durationMinutes không được để trống")
    @Min(value = 1, message = "Thời lượng thi tối thiểu là 1 phút")
    private Integer durationMinutes;

    @NotNull(message = "startTime không được để trống")
    private Instant startTime;

    @NotNull(message = "endTime không được để trống")
    private Instant endTime;

    private UUID semesterId;

    @Builder.Default
    private Boolean autoGenerateQuestions = true;

    private String title;
}
