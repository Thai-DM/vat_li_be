package com.vatly1.example.model.request;


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
public class AddExamQuestionDTO {

    @NotNull(message = "Question ID is required")
    private UUID questionId;

    private BigDecimal scoreWeight;

    private Integer orderIndex;
}