package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ProgressStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_student_class_material", columnNames = {"student_id", "class_id", "material_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProgressStatus status;

    @Column(name = "progress_percent", nullable = false)
    private BigDecimal progressPercent;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;
}
