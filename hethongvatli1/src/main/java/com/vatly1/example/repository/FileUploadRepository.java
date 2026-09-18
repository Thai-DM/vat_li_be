package com.vatly1.example.repository;

import com.vatly1.example.entity.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {
}
