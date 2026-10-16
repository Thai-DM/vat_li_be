package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_matrix")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamMatrix {

    @Column(name = "matrix_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID matrixId;

    @Column(name = "matrix_family_id", nullable = false)
    private UUID matrixFamilyId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "matrix_name")
    private String matrixName;

    @Column(name = "exam_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExamType examType = ExamType.PRACTICE;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MatrixStatus status = MatrixStatus.DRAFT;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "revision_number", nullable = false)
    @Builder.Default
    private Integer revisionNumber = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    @Builder.Default
    private Long rowVersion = 0L;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_points")
    private BigDecimal totalPoints;

    @Column(name = "created_by")
    private UUID createdBy;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.matrixFamilyId == null) {
            this.matrixFamilyId = UUID.randomUUID();
        }
        if (this.examType == null) {
            this.examType = ExamType.PRACTICE;
        }
        if (this.status == null) {
            this.status = MatrixStatus.DRAFT;
        }
        if (this.isLocked == null) {
            this.isLocked = Boolean.FALSE;
        }
        if (this.revisionNumber == null) {
            this.revisionNumber = 1;
        }
        if (this.rowVersion == null) {
            this.rowVersion = 0L;
        }
    }
}