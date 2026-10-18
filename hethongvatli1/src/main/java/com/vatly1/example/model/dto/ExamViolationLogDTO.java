package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.ViolationSeverity;
import com.vatly1.example.entity.enums.ViolationType;
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
public class ExamViolationLogDTO {
    private UUID logId;
    private UUID attemptId;
    private UUID examId;
    private UUID studentId;
    private String studentName;
    private String studentCode;
    private ViolationType violationType;
    private ViolationSeverity severity;
    private Integer violationCountAtTime;
    private String description;
    private String metadataJson;
    private Instant createdAt;
}
