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
public class ChunkUploadResponse {

    private UUID uploadId;
    private Integer chunkIndex;
    private Integer totalChunks;
    private Boolean uploaded;
    private String message;
}
