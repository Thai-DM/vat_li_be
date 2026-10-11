package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitSessionAnswerRequest {

    @NotNull(message = "questionId không được để trống")
    private UUID questionId;

    private List<UUID> selectedOptionIds;

    private String answerText;
}
