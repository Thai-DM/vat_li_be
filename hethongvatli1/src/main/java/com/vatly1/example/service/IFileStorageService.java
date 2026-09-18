package com.vatly1.example.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface IFileStorageService {

    /**
     * Tải file lên hệ thống lưu trữ (MinIO Object Storage hoặc local fallback).
     * @param file Tệp được tải lên từ request
     * @return URL công khai để truy cập file
     */
    String storeFile(MultipartFile file);

    /**
     * Tải file lên với thư mục tiền tố (vd: "materials", "experiments", "avatars", "images").
     */
    String storeFile(MultipartFile file, String folder);

    /**
     * Lấy InputStream của file để stream về người dùng hoặc xử lý nghiệp vụ.
     */
    InputStream getFileInputStream(String filename);

    /**
     * Xóa file khỏi hệ thống lưu trữ khi tài liệu hoặc minh chứng bị hủy.
     */
    void deleteFile(String fileUrlOrName);

    /**
     * Kiểm tra xem dịch vụ hiện tại có đang lưu trực tiếp lên MinIO hay không.
     */
    boolean isMinioEnabled();
}
