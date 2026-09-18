package com.vatly1.example.dto.response;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

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