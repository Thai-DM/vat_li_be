package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionDTO {
    
    @NotNull(message = "Subject ID is required")
    private UUID subjectId;
    
    @NotNull(message = "Topic ID is required")
    private UUID topicId;
    
    @NotNull(message = "Question type is required")
    private QuestionType questionType;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private String mediaUrl;
    
    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;
    
    private String cognitiveLevel;
    
    private List<CreateQuestionOptionDTO> options;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateQuestionOptionDTO {
        @NotBlank(message = "Option content is required")
        private String content;
        
        @NotNull(message = "isCorrect is required")
        private Boolean isCorrect;
        
        private Integer orderIndex;
        private String explanation;
    }
}