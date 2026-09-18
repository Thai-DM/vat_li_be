package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ExamQuestion.ExamQuestionId.class)
public class ExamQuestion {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ExamQuestionId implements java.io.Serializable {
        private java.util.UUID examId;
        private java.util.UUID questionId;
    }
    @Id
    @Column(name = "exam_id")
    private java.util.UUID examId;

    @Id
    @Column(name = "question_id")
    private java.util.UUID questionId;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "score_weight")
    private java.math.BigDecimal scoreWeight;


}
