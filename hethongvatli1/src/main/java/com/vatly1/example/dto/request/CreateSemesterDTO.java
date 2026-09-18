package com.vatly1.example.dto.request;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;
import com.vatly1.example.dto.response.*;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSemesterDTO {
    @NotBlank(message = "Mã học kỳ không được để trống")
    private String semesterCode;

    @NotBlank(message = "Tên học kỳ không được để trống")
    private String semesterName;

    @NotBlank(message = "Năm học không được để trống")
    private String academicYear;

    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
}
