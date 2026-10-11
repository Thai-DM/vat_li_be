package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.PracticeSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionResultDTO {
    private UUID sessionId;
    private PracticeSessionStatus status;
    private int totalQuestions;
    private int correctCount;
    private int wrongCount;
    private BigDecimal totalScore;
    private BigDecimal accuracyRate;
    private List<PracticeSessionQuestionDTO> questions;
    private List<WeakTopicDTO> updatedWeakTopics;
}
