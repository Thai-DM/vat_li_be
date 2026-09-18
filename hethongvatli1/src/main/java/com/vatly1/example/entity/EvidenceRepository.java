package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.EvidenceSourceType;
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
@Table(name = "evidence_repository")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceRepository {

    @Column(name = "evidence_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID evidenceId;

    @Column(name = "student_id")
    private java.util.UUID studentId;

    @Column(name = "source_type")
    @Enumerated(EnumType.STRING)
    private EvidenceSourceType sourceType;

    @Column(name = "source_id")
    private java.util.UUID sourceId;

    @Column(name = "file_id")
    private java.util.UUID fileId;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "created_at")
    private java.time.Instant createdAt;


}