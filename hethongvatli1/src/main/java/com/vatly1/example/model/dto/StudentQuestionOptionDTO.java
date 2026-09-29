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
public class StudentQuestionOptionDTO {
    private UUID optionId;
    private UUID questionId;
    private String content;
    private Integer orderIndex;
    private Boolean isCorrect; // null trong suốt quá trình làm bài (IN_PROGRESS) để chống lộ đề; chỉ trả về khi đã nộp/chấm điểm
}
