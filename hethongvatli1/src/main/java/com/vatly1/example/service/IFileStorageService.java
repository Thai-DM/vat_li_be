package com.vatly1.example.service;

import org.springframework.web.multipart.MultipartFile;

public interface IFileStorageService {
    String storeFile(MultipartFile file);
}
