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
@Table(name = "experiment_rubrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentRubric {

    @Column(name = "rubric_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID rubricId;

    @Column(name = "experiment_id")
    private java.util.UUID experimentId;

    @Column(name = "criteria_name")
    private String criteriaName;

    @Column(name = "max_score")
    private java.math.BigDecimal maxScore;

    @Column(name = "description")
    private String description;


}