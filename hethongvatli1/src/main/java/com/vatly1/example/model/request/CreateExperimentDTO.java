package com.vatly1.example.model.request;


import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExperimentDTO {

    @NotNull(message = "Subject ID is required")
    private UUID subjectId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String sceneAssetUrl;

    private JsonNode sceneAssetsJson;

    private String instructions;

    private Integer orderIndex;
}