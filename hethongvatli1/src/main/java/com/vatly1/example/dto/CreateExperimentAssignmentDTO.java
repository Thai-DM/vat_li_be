package com.vatly1.example.dto;

import jakarta.validation.constraints.NotNull;
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
public class CreateExperimentAssignmentDTO {
    @NotNull(message = "Class ID is required")
    private UUID classId;

    private Instant dueDate;

    private String instructionsOverride;
}
