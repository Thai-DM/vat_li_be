package com.vatly1.example.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemesterDTO {
    private java.util.UUID semesterId;
    private String semesterCode;
    private String semesterName;
    private String academicYear;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private Boolean isCurrent;
    private java.time.Instant createdAt;
}
