package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.DifficultyLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exam_matrix_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamMatrixDetail {

    @Column(name = "detail_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID detailId;

    @Column(name = "matrix_id")
    private java.util.UUID matrixId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;

    @Column(name = "num_questions")
    private Integer numQuestions;

    @Column(name = "weight_percent")
    private java.math.BigDecimal weightPercent;


}
