package com.vatly1.example.model.request;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicDTO {

    private UUID subjectId;

    @NotBlank(message = "Topic name is required")
    private String topicName;

    private Integer orderIndex;

    private String description;

}