package com.vatly1.example.service;

import com.vatly1.example.model.request.MergeChunksRequest;
import com.vatly1.example.model.response.ChunkUploadResponse;
import com.vatly1.example.model.response.ChunkUploadStatusResponse;
import com.vatly1.example.model.response.MergeChunksResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IChunkUploadService {

    /**
     * Tiếp nhận và lưu trữ một chunk vào thư mục tạm của phiên upload.
     */
    ChunkUploadResponse uploadChunk(
            UUID uploadId,
            int chunkIndex,
            int totalChunks,
            String fileName,
            MultipartFile chunk,
            UUID currentUserId
    );

    /**
     * Ghép toàn bộ các chunk đã upload thành file hoàn chỉnh.
     */
    MergeChunksResponse mergeChunks(
            MergeChunksRequest request,
            UUID currentUserId,
            String currentUserRole
    );

    /**
     * Kiểm tra tiến độ và danh sách chunk đã nhận / còn thiếu của phiên upload.
     */
    ChunkUploadStatusResponse getUploadStatus(
            UUID uploadId,
            UUID currentUserId,
            String currentUserRole
    );

    /**
     * Dọn dẹp định kỳ các phiên upload tạm quá hạn (24 giờ).
     */
    void cleanupExpiredSessions();
}
