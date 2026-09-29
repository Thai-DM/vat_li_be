package com.vatly1.example.model.response;

import com.vatly1.example.model.dto.MatrixValidationItemDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixValidationResultDTO {
    private UUID matrixId;
    private String matrixName;
    private boolean isValid;
    private int totalRequired;
    private long totalAvailable;
    private List<MatrixValidationItemDTO> items;
    private List<String> warnings;
}
