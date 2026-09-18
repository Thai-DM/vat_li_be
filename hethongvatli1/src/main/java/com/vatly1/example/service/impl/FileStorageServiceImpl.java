package com.vatly1.example.service.impl;

import com.vatly1.example.configuration.MinioConfig;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.service.IFileStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements IFileStorageService {

    private final MinioConfig minioConfig;
    private final MinioClient minioClient;
    private final Path localFileStorageLocation;

    @Autowired
    public FileStorageServiceImpl(
            MinioConfig minioConfig,
            @Autowired(required = false) MinioClient minioClient,
            @Value("${file.upload-dir:uploads}") String uploadDir) {
        this.minioConfig = minioConfig;
        this.minioClient = minioClient;
        this.localFileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.localFileStorageLocation);
        } catch (Exception ex) {
            log.warn("Could not create local upload directory: {}", ex.getMessage());
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        return storeFile(file, null);
    }

    @Override
    public String storeFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Tệp tải lên không được để trống", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename() != null ? StringUtils.cleanPath(file.getOriginalFilename()) : "file";
        if (originalFilename.contains("..")) {
            throw new CustomException("Tên tệp không hợp lệ: " + originalFilename, HttpStatus.BAD_REQUEST);
        }

        String fileExtension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            fileExtension = originalFilename.substring(i);
        }

        String generatedFilename = UUID.randomUUID().toString() + fileExtension;
        String objectName = (folder != null && !folder.isBlank()) ? folder + "/" + generatedFilename : generatedFilename;

        // 1. Thử tải lên MinIO nếu được kích hoạt
        if (minioConfig.isEnabled() && minioClient != null) {
            try {
                minioConfig.initBucket(minioClient);

                String contentType = file.getContentType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = "application/octet-stream";
                }

                try (InputStream is = file.getInputStream()) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(minioConfig.getBucketName())
                                    .object(objectName)
                                    .stream(is, file.getSize(), -1)
                                    .contentType(contentType)
                                    .build()
                    );
                }

                log.info("Uploaded file '{}' to MinIO bucket '{}' successfully.", objectName, minioConfig.getBucketName());
                return "/api/v1/files/" + objectName;
            } catch (Exception e) {
                log.warn("Failed to upload to MinIO, falling back to local storage: {}", e.getMessage());
                // Fallback to local disk below
            }
        }

        // 2. Lưu trữ cục bộ (Dùng cho môi trường Test hoặc khi MinIO tạm thời không khả dụng)
        try {
            Path targetLocation = this.localFileStorageLocation.resolve(generatedFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved file locally to '{}'", targetLocation);
            return "/api/v1/files/" + generatedFilename;
        } catch (IOException ex) {
            throw new CustomException("Không thể lưu trữ tệp " + originalFilename + ": " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public InputStream getFileInputStream(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new CustomException("Tên file không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        // 1. Thử lấy từ MinIO
        if (minioConfig.isEnabled() && minioClient != null) {
            try {
                return minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(filename)
                                .build()
                );
            } catch (Exception e) {
                log.debug("Object '{}' not found in MinIO, trying local fallback: {}", filename, e.getMessage());
            }
        }

        // 2. Lấy từ thư mục local
        try {
            Path filePath = this.localFileStorageLocation.resolve(filename).normalize();
            if (Files.exists(filePath)) {
                return Files.newInputStream(filePath);
            }
        } catch (IOException e) {
            log.warn("Error reading local file '{}': {}", filename, e.getMessage());
        }

        throw new CustomException("Không tìm thấy tệp yêu cầu: " + filename, HttpStatus.NOT_FOUND);
    }

    @Override
    public void deleteFile(String fileUrlOrName) {
        if (fileUrlOrName == null || fileUrlOrName.isBlank()) {
            return;
        }

        String objectName = fileUrlOrName;
        if (objectName.startsWith("/api/v1/files/")) {
            objectName = objectName.substring("/api/v1/files/".length());
        }

        if (minioConfig.isEnabled() && minioClient != null) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(objectName)
                                .build()
                );
                log.info("Deleted object '{}' from MinIO bucket '{}'", objectName, minioConfig.getBucketName());
            } catch (Exception e) {
                log.warn("Could not delete object '{}' from MinIO: {}", objectName, e.getMessage());
            }
        }

        try {
            Path filePath = this.localFileStorageLocation.resolve(objectName).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete local file '{}': {}", objectName, e.getMessage());
        }
    }

    @Override
    public boolean isMinioEnabled() {
        return minioConfig.isEnabled() && minioClient != null;
    }
}