package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixValidationItemDTO {
    private UUID topicId;
    private String topicName;
    private DifficultyLevel difficultyLevel;
    private int requiredQuestions;
    private long availableQuestions;
    private boolean isSufficient;
}
