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
@Table(name = "material_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialVersion {

    @Column(name = "version_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID versionId;

    @Column(name = "material_id")
    private java.util.UUID materialId;

    @Column(name = "version_no")
    private Integer versionNo;

    @Column(name = "content_snapshot")
    private String contentSnapshot;

    @Column(name = "changed_by")
    private java.util.UUID changedBy;

    @Column(name = "changed_at")
    private java.time.Instant changedAt;


}