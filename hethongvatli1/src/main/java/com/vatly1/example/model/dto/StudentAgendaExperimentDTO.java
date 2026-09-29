package com.vatly1.example.model.dto;

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
public class StudentAgendaExperimentDTO {
    private UUID assignmentId;
    private UUID experimentId;
    private String experimentTitle;
    private UUID classId;
    private String classCode;
    private Instant dueDate;
    private String submissionStatus;
}
