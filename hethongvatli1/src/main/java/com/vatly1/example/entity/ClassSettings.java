package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSettings {

    @Id
    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "practice_enabled", nullable = false)
    @Builder.Default
    private Boolean practiceEnabled = true;

    @Column(name = "quiz_enabled", nullable = false)
    @Builder.Default
    private Boolean quizEnabled = true;

    @Column(name = "max_practice_attempts", nullable = false)
    @Builder.Default
    private Integer maxPracticeAttempts = 10;

    @Column(name = "practice_show_solution", nullable = false)
    @Builder.Default
    private Boolean practiceShowSolution = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.updatedAt = Instant.now();
    }
}
