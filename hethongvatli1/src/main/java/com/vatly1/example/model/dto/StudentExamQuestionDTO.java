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
public class StudentExamQuestionDTO {
    private UUID examId;
    private UUID questionId;
    private String content;
    private QuestionType questionType;
    private DifficultyLevel difficultyLevel;
    private UUID topicId;
    private String topicName;
    private Integer orderIndex;
    private BigDecimal scoreWeight;
    private List<StudentQuestionOptionDTO> options;

    // Trạng thái trả lời của sinh viên cho lượt thi hiện hành
    private List<UUID> selectedOptionIds; // Các phương án sinh viên đã chọn/lưu tạm thời
    private String answerText;           // Câu trả lời tự luận ngắn (nếu có)
    private Boolean isCorrect;           // null khi đang thi; true/false khi đã nộp/chấm bài
    private BigDecimal score;            // Điểm đạt được của câu hỏi này sau khi chấm
}
