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
public class ExamParticipantDTO {
    private UUID id;
    private UUID examId;
    private String examTitle;
    private UUID studentId;
    private String studentUsername;
    private String studentName;
    private String studentCode;
    private UUID originalClassId;
    private String originalClassCode;
    private UUID targetClassId;
    private String targetClassCode;
    private String subjectName;
    private String reason;
    private UUID approvedBy;
    private String approverName;
    private Instant createdAt;
}
