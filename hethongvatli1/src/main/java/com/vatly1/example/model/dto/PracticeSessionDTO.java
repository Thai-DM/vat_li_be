package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.PracticeSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionDTO {
    private UUID sessionId;
    private UUID classId;
    private UUID studentId;
    private UUID subjectId;
    private PracticeSessionStatus status;
    private int totalQuestions;
    private BigDecimal totalScore;
    private BigDecimal accuracyRate;
    private Instant startedAt;
    private Instant completedAt;
    private List<PracticeSessionQuestionDTO> questions;

    private Boolean isDeficient;
    private String deficiencyNotice;
    private Boolean expandedTopicsUsed;
    private String expansionReason;
}
