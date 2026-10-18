package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ViolationSeverity;
import com.vatly1.example.entity.enums.ViolationType;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_violation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamViolationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "violation_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ViolationType violationType;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ViolationSeverity severity = ViolationSeverity.LOW;

    @Column(name = "violation_count_at_time", nullable = false)
    @Builder.Default
    private Integer violationCountAtTime = 1;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
