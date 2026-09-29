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
public class ExamAttemptDTO {
    private UUID attemptId;
    private UUID examId;
    private UUID studentId;
    private Integer attemptNumber;
    private Instant startedAt;
    private Instant submittedAt;
    private AttemptStatus status;
    private BigDecimal totalScore;

    // Thông tin kỳ thi và câu hỏi theo lượt thi (attempt)
    private String examTitle;
    private Integer durationMinutes;
    private Instant examStartTime;
    private Instant examEndTime;
    private Integer totalQuestions;
    private java.util.List<StudentExamQuestionDTO> questions;
}