package com.vatly1.example.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MergeChunksRequest {

    @NotNull(message = "uploadId không được để trống")
    private UUID uploadId;

    private String fileName;

    private Integer totalChunks;

    private Long fileSize;

    private String mimeType;
}
