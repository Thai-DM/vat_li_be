package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.ExamType;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExamMatrixDTO {
    private String matrixName;
    private ExamType examType;
    private String description;
    private BigDecimal totalPoints;

    @Valid
    private List<ExamMatrixDetailRequestDTO> details;
}
