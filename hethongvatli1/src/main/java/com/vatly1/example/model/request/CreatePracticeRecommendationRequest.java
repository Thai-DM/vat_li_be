package com.vatly1.example.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePracticeRecommendationRequest {

    @Min(value = 1, message = "Số lượng câu hỏi phải từ 1 đến 50")
    @Max(value = 50, message = "Số lượng câu hỏi không được vượt quá 50")
    private Integer questionCount;

    private UUID topicId;

    private Boolean allowExpandedSubjectTopics;
}
