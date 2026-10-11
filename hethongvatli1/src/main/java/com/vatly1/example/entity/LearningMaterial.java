package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.MaterialType;
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
@Table(name = "learning_materials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningMaterial {

    @Column(name = "material_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID materialId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "subject_id", nullable = false)
    private java.util.UUID subjectId;

    @Column(name = "scope", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private com.vatly1.example.entity.enums.MaterialScope scope = com.vatly1.example.entity.enums.MaterialScope.SHARED;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "file_id")
    private java.util.UUID fileId;

    @Column(name = "title")
    private String title;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private MaterialType type;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "approval_status")
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    @Column(name = "source_citation")
    private String sourceCitation;

    @Column(name = "created_by")
    private java.util.UUID createdBy;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;



}