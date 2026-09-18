package com.vatly1.example.dto.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionQualityDTO {
    private UUID questionId;
    private String questionText;
    private UUID topicId;
    private String topicName;
    private Integer timesUsed;
    private BigDecimal correctRate;
    private BigDecimal discriminationIndex;
    private BigDecimal avgTimeSeconds;
    private String qualityLabel; // "EXCELLENT", "GOOD", "FAIR", "POOR"
    private Instant updatedAt;
}