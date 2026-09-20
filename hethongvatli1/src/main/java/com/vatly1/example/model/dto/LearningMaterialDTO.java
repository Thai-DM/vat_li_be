package com.vatly1.example.model.dto;


import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.MaterialType;
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
public class LearningMaterialDTO {

    private UUID materialId;
    private UUID topicId;
    private UUID fileId;
    private String title;
    private MaterialType type;
    private String fileUrl;
    private String contentText;
    private Integer version;
    private ApprovalStatus approvalStatus;
    private String sourceCitation;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

}