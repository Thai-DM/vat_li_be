package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.DifficultyLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class ExamMatrixDetailRequestDTO {
    @NotNull(message = "topicId không được để trống")
    private UUID topicId;

    @NotNull(message = "difficultyLevel không được để trống (EASY, MEDIUM, HARD)")
    private DifficultyLevel difficultyLevel;

    @NotNull(message = "numQuestions không được để trống")
    @Min(value = 1, message = "numQuestions tối thiểu là 1")
    private Integer numQuestions;

    private BigDecimal weightPercent;
}
