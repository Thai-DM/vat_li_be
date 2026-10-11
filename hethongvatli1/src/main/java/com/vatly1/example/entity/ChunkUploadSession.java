package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ChunkUploadStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunk_upload_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadSession {

    @Id
    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ChunkUploadStatus status;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "final_file_id")
    private UUID finalFileId;

    @Column(name = "final_file_url", length = 1000)
    private String finalFileUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = ChunkUploadStatus.UPLOADING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
