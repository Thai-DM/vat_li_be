package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamMatrixDetailDTO {
    private UUID detailId;
    private UUID matrixId;
    private UUID topicId;
    private String topicName;
    private DifficultyLevel difficultyLevel;
    private Integer numQuestions;
    private BigDecimal weightPercent;
}
