package com.vatly1.example.model.response;

import com.vatly1.example.model.dto.StudentImportItemDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportResultDTO {
    private int totalRows;
    private int totalCreated;
    private int totalSkipped;
    private int totalEnrolled;
    private List<StudentImportItemDTO> students;
    private List<String> warnings;
    private List<String> errors;
}
