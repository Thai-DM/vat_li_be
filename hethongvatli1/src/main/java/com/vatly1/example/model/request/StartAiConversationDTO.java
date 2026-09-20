package com.vatly1.example.model.request;


import com.vatly1.example.entity.enums.AiMode;
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
public class StartAiConversationDTO {

    @NotNull(message = "Class ID is required")
    private UUID classId;

    private UUID topicId;

    private AiMode mode;
}