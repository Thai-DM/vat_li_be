package com.vatly1.example.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.vatly1.example.entity.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentSubmissionDTO {

    private UUID submissionId;
    private UUID assignmentId;
    private UUID experimentId;
    private String experimentTitle;
    private UUID classId;
    private String classCode;
    private Instant dueDate;

    // Student Info
    private UUID studentId;
    private String studentUsername;
    private String studentFullName;
    private String studentCode;
    private String studentEmail;

    // Submission details
    private Instant submittedAt;
    private String evidenceUrl;
    private UUID fileId;
    private JsonNode rawDataJson;
    private SubmissionStatus status;

    // Grading & Rubrics info
    private BigDecimal totalScore;
    private BigDecimal totalMaxScore;
    private Integer gradedRubricCount;
    private Integer totalRubricCount;
    private Boolean isGraded;

    // Detailed rubrics list (included in single submission view or when requested)
    private List<SubmissionRubricDTO> rubrics;

    // Confirmation Info
    private Boolean isConfirmed;
    private UUID confirmedBy;
    private Instant confirmedAt;
    private String confirmNote;
}
