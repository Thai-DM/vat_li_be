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
@Table(name = "experiment_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentScore {

    @Column(name = "score_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID scoreId;

    @Column(name = "submission_id")
    private java.util.UUID submissionId;

    @Column(name = "rubric_id")
    private java.util.UUID rubricId;

    @Column(name = "score")
    private java.math.BigDecimal score;

    @Column(name = "grader_id")
    private java.util.UUID graderId;

    @Column(name = "graded_at")
    private java.time.Instant gradedAt;

    @Column(name = "comment")
    private String comment;


}