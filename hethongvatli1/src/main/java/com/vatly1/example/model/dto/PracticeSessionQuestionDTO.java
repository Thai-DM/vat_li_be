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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionQuestionDTO {
    private UUID questionId;
    private String content;
    private QuestionType questionType;
    private DifficultyLevel difficultyLevel;
    private UUID topicId;
    private String topicName;
    private UUID chapterId;
    private String chapterName;
    private Integer orderIndex;
    private Boolean isReviewed;

    private List<StudentQuestionOptionDTO> options;
    private List<String> acceptedAnswers;

    private List<UUID> selectedOptionIds;
    private String answerText;

    private Boolean isCorrect;
    private BigDecimal score;
    private String explanation;
}
