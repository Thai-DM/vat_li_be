package com.vatly1.example.model.dto;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentDTO {
    private UUID experimentId;
    private UUID subjectId;
    private String title;
    private String description;
    private String sceneAssetUrl;
    private JsonNode sceneAssetsJson;
    private String instructions;
    private Integer orderIndex;
}