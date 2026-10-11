package com.vatly1.example.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.file.chunk")
@Data
public class ChunkUploadProperties {

    /**
     * Thư mục lưu tạm các chunk trước khi merge (mặc định: storage/temp-uploads)
     */
    private String tempDir = "storage/temp-uploads";

    /**
     * Thư mục lưu file hoàn chỉnh trên local disk (nếu không dùng MinIO)
     */
    private String completedDir = "storage/completed";

    /**
     * Kích thước chunk tối đa cho phép (mặc định: 100 KB = 102400 bytes)
     */
    private int maxChunkSize = 102400;

    /**
     * Tổng số chunk tối đa cho một file (mặc định: 10,000)
     */
    private int maxTotalChunks = 10000;

    /**
     * Tổng dung lượng file tối đa cho phép (mặc định: 50 MB = 52428800 bytes)
     */
    private long maxTotalFileSize = 52428800L;

    /**
     * Thời gian hết hạn của phiên upload tạm (mặc định: 24 giờ)
     */
    private int expirationHours = 24;
}
