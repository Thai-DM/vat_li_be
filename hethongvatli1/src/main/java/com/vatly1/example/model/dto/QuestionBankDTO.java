package com.vatly1.example.model.dto;


import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankDTO {
    private UUID questionId;
    private UUID subjectId;
    private UUID topicId;
    private QuestionType questionType;
    private String content;
    private String mediaUrl;
    private DifficultyLevel difficultyLevel;
    private String cognitiveLevel;
    private ApprovalStatus approvalStatus;
    private UUID createdBy;
    private Instant createdAt;
    
    private List<QuestionOptionDTO> options;
}