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

import java.math.BigDecimal;
import java.util.UUID;

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
    private UUID detailId;

    @Column(name = "matrix_id", nullable = false)
    private UUID matrixId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "clo_id")
    private UUID cloId;

    @Column(name = "difficulty_level", nullable = false)
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;

    @Column(name = "num_questions", nullable = false)
    private Integer numQuestions;

    @Column(name = "weight_percent")
    private BigDecimal weightPercent;
}