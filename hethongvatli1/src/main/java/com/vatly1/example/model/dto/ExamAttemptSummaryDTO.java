package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.AttemptStatus;
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
public class ExamAttemptSummaryDTO {
    private UUID attemptId;
    private UUID examId;
    private String examTitle;
    private UUID studentId;
    private String studentUsername;
    private String studentName;
    private String studentCode;
    private Integer attemptNumber;
    private AttemptStatus status;
    private Instant startedAt;
    private Instant submittedAt;
    private BigDecimal totalScore;
    private Integer totalQuestions;
    private Integer correctAnswersCount;
}
