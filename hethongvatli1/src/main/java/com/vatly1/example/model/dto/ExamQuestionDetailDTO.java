package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionDetailDTO {
    private UUID examId;
    private UUID questionId;
    private String content;
    private QuestionType questionType;
    private DifficultyLevel difficultyLevel;
    private UUID topicId;
    private String topicName;
    private Integer orderIndex;
    private BigDecimal scoreWeight;
    private List<QuestionOptionDTO> options;
}
