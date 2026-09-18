package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionStat {

    @Column(name = "question_id")
    @Id
    private UUID questionId;

    @Column(name = "times_used")
    private Integer timesUsed;

    @Column(name = "correct_rate")
    private BigDecimal correctRate;

    @Column(name = "discrimination_index")
    private BigDecimal discriminationIndex;

    @Column(name = "avg_time_seconds")
    private BigDecimal avgTimeSeconds;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
