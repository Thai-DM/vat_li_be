package com.vatly1.example.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeChunksResponse {

    private UUID uploadId;
    private String fileName;
    private Long fileSize;
    private Integer totalChunks;
    private String status;
    private String fileId;
    private String fileUrl;
}
