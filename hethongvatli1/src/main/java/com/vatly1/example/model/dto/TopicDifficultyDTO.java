package com.vatly1.example.model.dto;


import com.fasterxml.jackson.databind.JsonNode;
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
public class TopicDifficultyDTO {
    private UUID statId;
    private UUID subjectId;
    private UUID topicId;
    private String topicName;
    private UUID classId;
    private BigDecimal avgScore;
    private BigDecimal errorRate;
    private JsonNode commonWrongOptionsJson;
    private String period;
    private Instant generatedAt;
}