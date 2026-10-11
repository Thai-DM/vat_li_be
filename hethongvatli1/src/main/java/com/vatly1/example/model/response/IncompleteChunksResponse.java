package com.vatly1.example.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncompleteChunksResponse {

    private String status;
    private String message;
    private List<Integer> missingChunks;
}
