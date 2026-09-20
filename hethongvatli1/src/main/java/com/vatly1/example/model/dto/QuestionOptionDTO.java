package com.vatly1.example.model.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDTO {
    private UUID optionId;
    private UUID questionId;
    private String content;
    private Boolean isCorrect;
    private Integer orderIndex;
    private String explanation;
}