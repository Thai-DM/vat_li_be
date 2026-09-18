package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ProgressStatus;
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
@Table(name = "learning_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningProgress {

    @Column(name = "progress_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID progressId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ProgressStatus status;

    @Column(name = "progress_percent")
    private java.math.BigDecimal progressPercent;

    @Column(name = "last_accessed_at")
    private java.time.Instant lastAccessedAt;


}