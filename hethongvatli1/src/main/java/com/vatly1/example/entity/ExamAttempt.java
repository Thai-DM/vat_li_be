package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "student_id", "attempt_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Column(name = "attempt_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID attemptId;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AttemptStatus status;

    @Column(name = "total_score")
    private BigDecimal totalScore;
}
