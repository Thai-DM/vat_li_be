package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.FileProcessingStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "file_uploads")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUpload {

    @Column(name = "file_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID fileId;

    @Column(name = "uploader_id")
    private java.util.UUID uploaderId;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "stored_url")
    private String storedUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private java.util.UUID entityId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FileProcessingStatus status = FileProcessingStatus.COMPLETED;

    @Column(name = "progress_percent")
    @Builder.Default
    private java.math.BigDecimal progressPercent = java.math.BigDecimal.valueOf(100);

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "metadata_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode metadataJson;

    @Column(name = "created_at")
    @Builder.Default
    private java.time.Instant createdAt = java.time.Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private java.time.Instant updatedAt = java.time.Instant.now();

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = java.time.Instant.now();
        if (this.updatedAt == null) this.updatedAt = java.time.Instant.now();
        if (this.status == null) this.status = FileProcessingStatus.COMPLETED;
        if (this.progressPercent == null) this.progressPercent = java.math.BigDecimal.valueOf(100);
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = java.time.Instant.now();
    }
}