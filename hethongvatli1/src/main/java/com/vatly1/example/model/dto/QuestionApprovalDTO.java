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
public class QuestionApprovalDTO {
    private UUID approvalId;
    private UUID questionId;
    private UUID actorId;
    private String actorName;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private Instant createdAt;
}
