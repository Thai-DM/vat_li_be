package com.vatly1.example.model.dto;


import com.vatly1.example.entity.enums.ProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningProgressDTO {
    private UUID progressId;
    private UUID studentId;
    private UUID classId;
    private UUID topicId;
    private ProgressStatus status;
    private BigDecimal progressPercent;
    private Instant lastAccessedAt;
}