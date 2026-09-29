package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttemptPolicyDTO {
    private UUID examId;
    private String examTitle;
    private ExamType examType;
    private int maxAttempts;
    private int usedAttempts;
    private int remainingAttempts;
    private boolean canStartAttempt;
    private boolean hasInProgressAttempt;
    private UUID currentAttemptId;
    private Instant startTime;
    private Instant endTime;
    @com.fasterxml.jackson.annotation.JsonProperty("isStarted")
    private boolean isStarted;
    @com.fasterxml.jackson.annotation.JsonProperty("isEnded")
    private boolean isEnded;
    private Integer durationMinutes;
}
