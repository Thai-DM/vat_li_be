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
public class CloDTO {
    private UUID cloId;
    private UUID subjectId;
    private String cloCode;
    private String description;
    private Integer orderIndex;
    private Boolean isActive;
    private Long questionCount;
    private Instant createdAt;
    private Instant updatedAt;
}
