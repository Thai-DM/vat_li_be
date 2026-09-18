package com.vatly1.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportResultDTO {
    private int totalParsed;
    private int totalImported;
    private List<QuestionBankDTO> questions;
    private List<String> warnings;
}
