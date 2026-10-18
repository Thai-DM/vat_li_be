package com.vatly1.example.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationResultDTO {
    private Integer currentViolationCount;
    private Integer maxViolationsAllowed;
    private Boolean isDisqualified;
    private String message;
}
