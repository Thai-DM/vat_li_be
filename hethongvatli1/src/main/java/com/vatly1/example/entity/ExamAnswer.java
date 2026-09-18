package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAnswer {

    @Column(name = "answer_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID answerId;

    @Column(name = "attempt_id")
    private java.util.UUID attemptId;

    @Column(name = "question_id")
    private java.util.UUID questionId;

    @Column(name = "selected_option_ids")
    private java.util.List<java.util.UUID> selectedOptionIds;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "score")
    private java.math.BigDecimal score;


}
