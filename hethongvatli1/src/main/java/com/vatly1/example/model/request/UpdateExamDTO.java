package com.vatly1.example.model.request;

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
public class UpdateExamDTO {
    private String title;
    private ExamType examType;
    private Integer durationMinutes;
    private Integer maxAttempts;
    private Instant startTime;
    private Instant endTime;
    private UUID matrixId;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Boolean isPublished;
}
