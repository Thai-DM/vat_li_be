package com.vatly1.example.model.response;

import com.vatly1.example.model.dto.ExamDTO;
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
public class BulkProvisionFinalExamResultDTO {
    private UUID matrixId;
    private String matrixName;
    private Integer revisionNumber;
    private int totalClassesRequested;
    private int totalClassesProvisioned;
    private List<ExamDTO> createdExams;
}
