package com.vatly1.example.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

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
public class ExperimentAssignmentDTO {
    private UUID assignmentId;
    private UUID experimentId;
    private UUID classId;
    private UUID assignedBy;
    private Instant dueDate;
    private String instructionsOverride;
    private Instant createdAt;
}
