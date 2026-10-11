package com.vatly1.example.service.impl;

import com.vatly1.example.configuration.ChunkUploadProperties;
import com.vatly1.example.entity.ChunkUploadSession;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.enums.ChunkUploadStatus;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.exception.MissingChunksException;
import com.vatly1.example.model.request.MergeChunksRequest;
import com.vatly1.example.model.response.ChunkUploadResponse;
import com.vatly1.example.model.response.ChunkUploadStatusResponse;
import com.vatly1.example.model.response.MergeChunksResponse;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.IChunkUploadSessionRepository;
import com.vatly1.example.service.IChunkUploadService;
import com.vatly1.example.service.IFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUploadServiceImpl implements IChunkUploadService {

    private final IChunkUploadSessionRepository sessionRepository;
    private final FileUploadRepository fileUploadRepository;
    private final IFileStorageService fileStorageService;
    private final ChunkUploadProperties properties;
    private final ChunkUploadSessionManager sessionManager;

    @Override
    @Transactional
    public ChunkUploadResponse uploadChunk(
            UUID uploadId,
            int chunkIndex,
            int totalChunks,
            String fileName,
            MultipartFile chunk,
            UUID currentUserId) {

        if (uploadId == null) {
            throw new CustomException("uploadId không hợp lệ hoặc để trống", HttpStatus.BAD_REQUEST);
        }
        if (totalChunks <= 0 || totalChunks > properties.getMaxTotalChunks()) {
            throw new CustomException("totalChunks không hợp lệ (phải từ 1 đến " + properties.getMaxTotalChunks() + ")", HttpStatus.BAD_REQUEST);
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new CustomException("chunkIndex không hợp lệ (phải từ 0 đến " + (totalChunks - 1) + ")", HttpStatus.BAD_REQUEST);
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new CustomException("Dữ liệu chunk không được để trống", HttpStatus.BAD_REQUEST);
        }
        if (chunk.getSize() > properties.getMaxChunkSize()) {
            throw new CustomException("Kích thước chunk vượt quá giới hạn tối đa cho phép (" + properties.getMaxChunkSize() + " bytes)", HttpStatus.BAD_REQUEST);
        }
        if ((long) totalChunks * properties.getMaxChunkSize() > properties.getMaxTotalFileSize()) {
            throw new CustomException("Dung lượng ước tính của file vượt quá giới hạn tối đa cho phép (" + properties.getMaxTotalFileSize() + " bytes)", HttpStatus.BAD_REQUEST);
        }
        if (currentUserId == null) {
            throw new CustomException("Yêu cầu đăng nhập để tải lên tệp", HttpStatus.UNAUTHORIZED);
        }

        String cleanFileName = validateAndCleanFilename(fileName);

        // 1. Quản lý phiên upload bằng sessionManager (chạy trong transaction con độc lập)
        ChunkUploadSession session = sessionManager.getOrCreateSession(
                uploadId,
                currentUserId,
                cleanFileName,
                totalChunks,
                properties.getExpirationHours()
        );

        // 2. Xác thực quyền sở hữu và tính nhất quán metadata
        if (!Objects.equals(session.getOwnerId(), currentUserId)) {
            throw new CustomException("Bạn không có quyền thao tác trên phiên upload của người khác", HttpStatus.FORBIDDEN);
        }
        if (!Objects.equals(session.getOriginalFilename(), cleanFileName)) {
            throw new CustomException("Metadata không nhất quán: fileName '" + cleanFileName + "' không khớp với phiên upload ban đầu ('" + session.getOriginalFilename() + "')", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(session.getTotalChunks(), totalChunks)) {
            throw new CustomException("Metadata không nhất quán: totalChunks '" + totalChunks + "' không khớp với phiên upload ban đầu (" + session.getTotalChunks() + ")", HttpStatus.CONFLICT);
        }
        if (session.getStatus() == ChunkUploadStatus.MERGING) {
            throw new CustomException("Phiên upload đang được ghép file. Không thể tải thêm chunk trong lúc ghép", HttpStatus.CONFLICT);
        }
        if (session.getStatus() == ChunkUploadStatus.COMPLETED) {
            throw new CustomException("Phiên upload đã hoàn tất và đã được ghép file", HttpStatus.BAD_REQUEST);
        }

        // 3. Lưu trữ chunk vào thư mục tạm an toàn
        Path tempDir = getUploadTempDir(uploadId);
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new CustomException("Không thể tạo thư mục tạm: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Path partFile = tempDir.resolve(chunkIndex + ".part");
        byte[] chunkBytes;
        try {
            chunkBytes = chunk.getBytes();
        } catch (IOException e) {
            throw new CustomException("Không thể đọc dữ liệu chunk: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 4. Kiểm tra Idempotent Retry vs 409 Conflict nếu partFile đã tồn tại
        if (Files.exists(partFile)) {
            try {
                byte[] existingBytes = Files.readAllBytes(partFile);
                if (Arrays.equals(existingBytes, chunkBytes)) {
                    log.info("Chunk {} of uploadId {} already uploaded with identical content (idempotent retry).", chunkIndex, uploadId);
                    return ChunkUploadResponse.builder()
                            .uploadId(uploadId)
                            .chunkIndex(chunkIndex)
                            .totalChunks(totalChunks)
                            .uploaded(true)
                            .message("Chunk already uploaded (idempotent retry)")
                            .build();
                } else {
                    log.warn("Conflict: Chunk {} of uploadId {} exists with different content.", chunkIndex, uploadId);
                    throw new CustomException("Chunk với index " + chunkIndex + " đã tồn tại với nội dung khác nhau", HttpStatus.CONFLICT);
                }
            } catch (IOException e) {
                throw new CustomException("Lỗi kiểm tra chunk hiện có: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // 5. Ghi chunk an toàn qua tệp tạm ngẫu nhiên UUID để chống xung đột race-condition I/O
        Path tempPartFile = tempDir.resolve(chunkIndex + ".part.tmp." + UUID.randomUUID());
        try {
            Files.write(tempPartFile, chunkBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            // Kiểm tra lại nếu luồng khác vừa ghi xong partFile đúng khoảnh khắc này
            if (Files.exists(partFile)) {
                Files.deleteIfExists(tempPartFile);
                byte[] existingBytes = Files.readAllBytes(partFile);
                if (Arrays.equals(existingBytes, chunkBytes)) {
                    return ChunkUploadResponse.builder()
                            .uploadId(uploadId)
                            .chunkIndex(chunkIndex)
                            .totalChunks(totalChunks)
                            .uploaded(true)
                            .message("Chunk already uploaded (idempotent retry)")
                            .build();
                } else {
                    throw new CustomException("Chunk với index " + chunkIndex + " đã tồn tại với nội dung khác nhau", HttpStatus.CONFLICT);
                }
            }

            try {
                Files.move(tempPartFile, partFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPartFile, partFile);
            } catch (FileAlreadyExistsException e) {
                Files.deleteIfExists(tempPartFile);
                byte[] existingBytes = Files.readAllBytes(partFile);
                if (Arrays.equals(existingBytes, chunkBytes)) {
                    return ChunkUploadResponse.builder()
                            .uploadId(uploadId)
                            .chunkIndex(chunkIndex)
                            .totalChunks(totalChunks)
                            .uploaded(true)
                            .message("Chunk already uploaded (idempotent retry)")
                            .build();
                } else {
                    throw new CustomException("Chunk với index " + chunkIndex + " đã tồn tại với nội dung khác nhau", HttpStatus.CONFLICT);
                }
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPartFile);
            } catch (Exception ignored) {}
            throw new CustomException("Lỗi ghi chunk vào đĩa: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Saved chunk {}/{} for uploadId {} ({} bytes)", chunkIndex, totalChunks, uploadId, chunkBytes.length);

        return ChunkUploadResponse.builder()
                .uploadId(uploadId)
                .chunkIndex(chunkIndex)
                .totalChunks(totalChunks)
                .uploaded(true)
                .message("Chunk uploaded successfully")
                .build();
    }

    @Override
    @Transactional
    public MergeChunksResponse mergeChunks(
            MergeChunksRequest request,
            UUID currentUserId,
            String currentUserRole) {

        if (request == null || request.getUploadId() == null) {
            throw new CustomException("uploadId không được để trống", HttpStatus.BAD_REQUEST);
        }
        UUID uploadId = request.getUploadId();

        ChunkUploadSession session = sessionRepository.findByIdWithLock(uploadId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên upload", HttpStatus.NOT_FOUND));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUserRole);
        if (!isAdmin && !Objects.equals(session.getOwnerId(), currentUserId)) {
            throw new CustomException("Bạn không có quyền truy cập phiên upload của người khác", HttpStatus.FORBIDDEN);
        }

        if (session.getStatus() == ChunkUploadStatus.COMPLETED) {
            log.info("Upload session {} already completed, returning existing metadata.", uploadId);
            return MergeChunksResponse.builder()
                    .uploadId(session.getUploadId())
                    .fileName(session.getOriginalFilename())
                    .fileSize(session.getFileSizeBytes())
                    .totalChunks(session.getTotalChunks())
                    .status("COMPLETED")
                    .fileId(session.getFinalFileId() != null ? session.getFinalFileId().toString() : null)
                    .fileUrl(session.getFinalFileUrl())
                    .build();
        }

        if (session.getStatus() == ChunkUploadStatus.MERGING) {
            throw new CustomException("Phiên upload đang được ghép file", HttpStatus.CONFLICT);
        }

        Path tempDir = getUploadTempDir(uploadId);
        if (!Files.exists(tempDir)) {
            throw new CustomException("Không tìm thấy thư mục tạm của phiên upload", HttpStatus.NOT_FOUND);
        }

        int totalChunks = session.getTotalChunks();
        List<Integer> missingChunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            Path part = tempDir.resolve(i + ".part");
            if (!Files.exists(part)) {
                missingChunks.add(i);
            }
        }

        if (!missingChunks.isEmpty()) {
            log.warn("Cannot merge uploadId {}: missing {} chunks", uploadId, missingChunks.size());
            throw new MissingChunksException("Missing chunks", missingChunks);
        }

        // Khóa phiên sang MERGING để ngăn chặn chunk mới hoặc request merge thứ 2
        session.setStatus(ChunkUploadStatus.MERGING);
        session = sessionRepository.saveAndFlush(session);

        Path mergingFile = tempDir.resolve(uploadId + ".merging");
        try {
            // 1. Ghép tuần tự các chunk theo đúng index bằng Buffered Streaming
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(mergingFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), 64 * 1024)) {
                byte[] buffer = new byte[8192];
                for (int i = 0; i < totalChunks; i++) {
                    Path part = tempDir.resolve(i + ".part");
                    try (InputStream in = new BufferedInputStream(Files.newInputStream(part), 64 * 1024)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                out.flush();
            }

            long totalSize = Files.size(mergingFile);
            String contentType = determineContentType(session.getOriginalFilename());

            // 2. Tải file hoàn chỉnh lên hệ thống lưu trữ (MinIO hoặc local storage)
            String storedUrl;
            try (InputStream is = new BufferedInputStream(Files.newInputStream(mergingFile), 64 * 1024)) {
                storedUrl = fileStorageService.storeFile(is, session.getOriginalFilename(), totalSize, contentType, "chunked");
            }

            // 3. Đăng ký metadata FileUpload chính thức
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(session.getOwnerId())
                    .originalFilename(session.getOriginalFilename())
                    .storedUrl(storedUrl)
                    .fileSizeBytes(totalSize)
                    .mimeType(contentType)
                    .purpose("CHUNKED_UPLOAD")
                    .status(FileProcessingStatus.COMPLETED)
                    .build();
            fileUpload = fileUploadRepository.save(fileUpload);

            // 4. Chuyển trạng thái phiên sang COMPLETED
            session.setStatus(ChunkUploadStatus.COMPLETED);
            session.setFileSizeBytes(totalSize);
            session.setFinalFileId(fileUpload.getFileId());
            session.setFinalFileUrl(storedUrl);
            sessionRepository.save(session);

            // 5. Đăng ký dọn dẹp sau commit và phục hồi khi rollback thông qua TransactionSynchronizationManager
            final String finalStoredUrl = storedUrl;
            final Path finalTempDir = tempDir;
            final Path finalMergingFile = mergingFile;

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // DB đã COMMIT THÀNH CÔNG 100%! Lúc này mới xóa an toàn thư mục chunk tạm
                        try {
                            Files.deleteIfExists(finalMergingFile);
                        } catch (Exception ignored) {}
                        deleteDirectoryQuietly(finalTempDir);
                        log.info("Merge transaction committed successfully for uploadId {}. Cleaned up temp chunk dir.", uploadId);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            log.warn("Merge transaction rolled back for uploadId {}. Cleaning up orphan storage file {}", uploadId, finalStoredUrl);
                            try {
                                fileStorageService.deleteFile(finalStoredUrl);
                            } catch (Exception e) {
                                log.error("Failed to delete orphan file {}: {}", finalStoredUrl, e.getMessage());
                            }
                            try {
                                Files.deleteIfExists(finalMergingFile);
                            } catch (Exception ignored) {}
                            // GIỮ NGUYÊN các file .part trong finalTempDir để Frontend có thể retry
                        }
                    }
                });
            } else {
                // Trường hợp non-transactional: dọn trực tiếp
                try {
                    Files.deleteIfExists(mergingFile);
                } catch (Exception ignored) {}
                deleteDirectoryQuietly(tempDir);
            }

            log.info("Successfully merged uploadId {} for file {} ({} bytes, url: {})",
                    uploadId, session.getOriginalFilename(), totalSize, storedUrl);

            return MergeChunksResponse.builder()
                    .uploadId(session.getUploadId())
                    .fileName(session.getOriginalFilename())
                    .fileSize(totalSize)
                    .totalChunks(totalChunks)
                    .status("COMPLETED")
                    .fileId(fileUpload.getFileId().toString())
                    .fileUrl(storedUrl)
                    .build();

        } catch (Exception ex) {
            log.error("Merge failed for uploadId {}: {}. Retaining chunks for retry.", uploadId, ex.getMessage(), ex);

            // Xóa file .merging dở dang nếu có
            try {
                Files.deleteIfExists(mergingFile);
            } catch (IOException ignored) {}

            // Phục hồi trạng thái UPLOADING trong transaction riêng biệt để FE có thể thử lại ngay lập tức
            sessionManager.resetStatus(uploadId, ChunkUploadStatus.UPLOADING);

            if (ex instanceof CustomException) {
                throw (CustomException) ex;
            }
            throw new CustomException("Lỗi ghép tệp: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChunkUploadStatusResponse getUploadStatus(
            UUID uploadId,
            UUID currentUserId,
            String currentUserRole) {

        if (uploadId == null) {
            throw new CustomException("uploadId không được để trống", HttpStatus.BAD_REQUEST);
        }

        ChunkUploadSession session = sessionRepository.findById(uploadId)
                .orElseThrow(() -> new CustomException("Không tìm thấy phiên upload", HttpStatus.NOT_FOUND));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUserRole);
        if (!isAdmin && !Objects.equals(session.getOwnerId(), currentUserId)) {
            throw new CustomException("Bạn không có quyền truy cập phiên upload của người khác", HttpStatus.FORBIDDEN);
        }

        Path tempDir = getUploadTempDir(uploadId);
        List<Integer> uploadedChunks = new ArrayList<>();
        List<Integer> missingChunks = new ArrayList<>();

        int total = session.getTotalChunks();
        if (Files.exists(tempDir)) {
            for (int i = 0; i < total; i++) {
                if (Files.exists(tempDir.resolve(i + ".part"))) {
                    uploadedChunks.add(i);
                } else {
                    missingChunks.add(i);
                }
            }
        } else if (session.getStatus() == ChunkUploadStatus.COMPLETED) {
            for (int i = 0; i < total; i++) {
                uploadedChunks.add(i);
            }
        } else {
            for (int i = 0; i < total; i++) {
                missingChunks.add(i);
            }
        }

        return ChunkUploadStatusResponse.builder()
                .uploadId(session.getUploadId())
                .fileName(session.getOriginalFilename())
                .totalChunks(total)
                .uploadedChunks(uploadedChunks)
                .missingChunks(missingChunks)
                .status(session.getStatus().name())
                .build();
    }

    @Override
    @Scheduled(cron = "${app.file.chunk.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();

        // 1. Dọn các phiên đã quá hạn (bỏ qua MERGING đang hoạt động bình thường)
        List<ChunkUploadSession> expiredSessions = sessionRepository.findByStatusNotAndExpiresAtBefore(
                ChunkUploadStatus.MERGING, now);

        for (ChunkUploadSession s : expiredSessions) {
            if (s.getStatus() == ChunkUploadStatus.MERGING) {
                continue; // Tuyệt đối không dọn phiên đang MERGING
            }
            Path tempDir = getUploadTempDir(s.getUploadId());
            deleteDirectoryQuietly(tempDir);
            if (s.getStatus() != ChunkUploadStatus.COMPLETED) {
                s.setStatus(ChunkUploadStatus.EXPIRED);
                sessionRepository.save(s);
            }
            log.info("Cleaned up expired chunk upload session {}", s.getUploadId());
        }

        // 2. Xử lý các zombie MERGING sessions do server crash trong quá khứ
        // Nếu một phiên ở MERGING mà đã quá hạn (expiresAt < now) -> server crash trong lúc merge
        List<ChunkUploadSession> zombieMergingSessions = sessionRepository.findByStatusAndExpiresAtBefore(
                ChunkUploadStatus.MERGING, now);
        for (ChunkUploadSession s : zombieMergingSessions) {
            Path tempDir = getUploadTempDir(s.getUploadId());
            deleteDirectoryQuietly(tempDir);
            s.setStatus(ChunkUploadStatus.FAILED);
            sessionRepository.save(s);
            log.warn("Cleaned up zombie MERGING session {} caused by previous server crash", s.getUploadId());
        }
    }

    private Path getUploadTempDir(UUID uploadId) {
        Path baseTemp = Paths.get(properties.getTempDir()).toAbsolutePath().normalize();
        Path tempDir = baseTemp.resolve(uploadId.toString()).normalize();
        if (!tempDir.startsWith(baseTemp)) {
            throw new CustomException("Đường dẫn lưu trữ tạm không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return tempDir;
    }

    private String validateAndCleanFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new CustomException("Tên tệp không được để trống", HttpStatus.BAD_REQUEST);
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new CustomException("Tên tệp không hợp lệ: chứa ký tự duyệt thư mục (path traversal)", HttpStatus.BAD_REQUEST);
        }
        String clean = StringUtils.cleanPath(fileName);
        if (clean.contains("..")) {
            throw new CustomException("Tên tệp không hợp lệ: chứa ký tự duyệt thư mục (path traversal)", HttpStatus.BAD_REQUEST);
        }
        return clean;
    }

    private String determineContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk temp directory {}: {}", dir, e.getMessage());
        }
    }
}
