package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.ExamType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateExamMatrixDTO {
    @NotNull(message = "subjectId không được để trống")
    private UUID subjectId;

    @NotBlank(message = "matrixName không được để trống")
    private String matrixName;

    private ExamType examType;

    private String description;

    private BigDecimal totalPoints;

    @NotEmpty(message = "Danh sách chi tiết ma trận (details) không được để trống")
    @Valid
    private List<ExamMatrixDetailRequestDTO> details;
}
