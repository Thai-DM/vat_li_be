package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingTaskDTO {
    private String taskId;
    private String taskType; // EXAM, EXPERIMENT, MATERIAL
    private String title;
    private String courseName;
    private String classCode;
    private Instant deadline;
    private String status; // PENDING, COMPLETED, OVERDUE, IN_PROGRESS
    private String priority; // HIGH, MEDIUM, LOW
    private String actionUrl;
}
