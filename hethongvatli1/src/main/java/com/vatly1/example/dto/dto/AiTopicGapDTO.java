package com.vatly1.example.dto.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

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
public class AiTopicGapDTO {
    private UUID gapId;
    private UUID subjectId;
    private UUID topicId;
    private String topicName;
    private Integer refusalCount;
    private String frequentQuerySample;
    private String period;
    private Instant generatedAt;
}