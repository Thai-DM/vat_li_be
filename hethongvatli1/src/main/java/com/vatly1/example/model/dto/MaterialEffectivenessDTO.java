package com.vatly1.example.model.dto;

import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialEffectivenessDTO {
    private UUID materialId;
    private String title;
    private UUID topicId;
    private String topicName;
    private String period;
    private Integer viewCount;
    private BigDecimal avgTimeSpentSeconds;
    private BigDecimal correlatedScoreImprovement;
}