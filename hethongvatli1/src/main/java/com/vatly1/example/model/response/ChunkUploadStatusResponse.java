package com.vatly1.example.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadStatusResponse {

    private UUID uploadId;
    private String fileName;
    private Integer totalChunks;
    private List<Integer> uploadedChunks;
    private List<Integer> missingChunks;
    private String status;
}
