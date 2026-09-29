package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.AttemptStatus;
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
public class ExamAttemptProgressDTO {
    private UUID attemptId;
    private UUID examId;
    private String examTitle;
    private AttemptStatus status;
    private int totalQuestions;
    private int answeredQuestions;
    private Instant startedAt;
    private Instant submittedAt;
    private Integer durationMinutes;
    private Instant expiresAt;
    private long remainingSeconds;
    @com.fasterxml.jackson.annotation.JsonProperty("isExpired")
    private boolean isExpired;
}
