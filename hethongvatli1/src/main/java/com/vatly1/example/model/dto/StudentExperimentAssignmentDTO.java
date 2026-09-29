package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.SubmissionStatus;
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
public class StudentExperimentAssignmentDTO {
    private UUID assignmentId;
    private UUID experimentId;
    private String experimentTitle;
    private String experimentDescription;
    private UUID classId;
    private String classCode;
    private String className;
    private Instant dueDate;
    private String instructionsOverride;
    @com.fasterxml.jackson.annotation.JsonProperty("isOverdue")
    private boolean isOverdue;

    // Submission info
    private UUID submissionId;
    private SubmissionStatus submissionStatus;
    private Instant submittedAt;
    private String evidenceUrl;
    private UUID fileId;
    private BigDecimal score;
    private String feedback;
}
