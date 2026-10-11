package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSettingsDTO {
    private UUID classId;
    private Boolean practiceEnabled;
    private Boolean quizEnabled;
    private Integer maxPracticeAttempts;
    private Boolean practiceShowSolution;
    private Instant updatedAt;
    private UUID updatedBy;
}
