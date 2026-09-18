package com.vatly1.example.service.impl;

import com.vatly1.example.exception.CustomException;
import com.vatly1.example.service.IFileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements IFileStorageService {

    private final Path fileStorageLocation;

    public FileStorageServiceImpl(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new CustomException("Could not create the directory where the uploaded files will be stored.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        
        try {
            if (originalFilename.contains("..")) {
                throw new CustomException("Sorry! Filename contains invalid path sequence " + originalFilename, HttpStatus.BAD_REQUEST);
            }

            String fileExtension = "";
            int i = originalFilename.lastIndexOf('.');
            if (i > 0) {
                fileExtension = originalFilename.substring(i);
            }
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path targetLocation = this.fileStorageLocation.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/files/" + newFilename;
        } catch (IOException ex) {
            throw new CustomException("Could not store file " + originalFilename + ". Please try again!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
