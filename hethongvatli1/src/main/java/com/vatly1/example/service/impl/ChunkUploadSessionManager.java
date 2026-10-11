package com.vatly1.example.service.impl;

import com.vatly1.example.entity.ChunkUploadSession;
import com.vatly1.example.entity.enums.ChunkUploadStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IChunkUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkUploadSessionManager {

    private final IChunkUploadSessionRepository sessionRepository;

    /**
     * Khởi tạo hoặc lấy phiên upload trong transaction riêng biệt (REQUIRES_NEW).
     * Ngăn chặn việc bắt DataIntegrityViolationException làm đánh dấu rollback-only trên transaction chính.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChunkUploadSession getOrCreateSession(
            UUID uploadId,
            UUID currentUserId,
            String cleanFileName,
            int totalChunks,
            long expirationHours) {

        Optional<ChunkUploadSession> sessionOpt = sessionRepository.findByIdWithLock(uploadId);
        if (sessionOpt.isPresent()) {
            return sessionOpt.get();
        }

        ChunkUploadSession session = ChunkUploadSession.builder()
                .uploadId(uploadId)
                .ownerId(currentUserId)
                .originalFilename(cleanFileName)
                .totalChunks(totalChunks)
                .status(ChunkUploadStatus.UPLOADING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS))
                .build();

        try {
            return sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent first-chunk creation detected for uploadId {}, retrieving existing session.", uploadId);
            return sessionRepository.findByIdWithLock(uploadId)
                    .orElseThrow(() -> new CustomException("Lỗi khởi tạo phiên upload", HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Cập nhật trạng thái session trong transaction riêng biệt (REQUIRES_NEW),
     * dùng khi cần reset trạng thái về UPLOADING khi có lỗi xảy ra.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetStatus(UUID uploadId, ChunkUploadStatus newStatus) {
        sessionRepository.findById(uploadId).ifPresent(s -> {
            s.setStatus(newStatus);
            sessionRepository.saveAndFlush(s);
        });
    }
}
