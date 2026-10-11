package com.vatly1.example.repository;

import com.vatly1.example.entity.ChunkUploadSession;
import com.vatly1.example.entity.enums.ChunkUploadStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChunkUploadSessionRepository extends JpaRepository<ChunkUploadSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChunkUploadSession s WHERE s.uploadId = :uploadId")
    Optional<ChunkUploadSession> findByIdWithLock(@Param("uploadId") UUID uploadId);

    List<ChunkUploadSession> findByStatusNotAndExpiresAtBefore(ChunkUploadStatus status, Instant now);

    List<ChunkUploadSession> findByStatusAndExpiresAtBefore(ChunkUploadStatus status, Instant now);

    long countByOwnerIdAndStatus(UUID ownerId, ChunkUploadStatus status);
}
