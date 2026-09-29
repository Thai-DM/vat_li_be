package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadUrlDTO {
    private UUID fileId;
    private String fileName;
    private String mimeType;
    private Long fileSizeBytes;
    private String downloadUrl;
    private Instant expiresAt;
}
