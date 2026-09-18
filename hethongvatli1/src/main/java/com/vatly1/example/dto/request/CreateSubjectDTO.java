package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
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
public class CreateSubjectDTO {
    @NotBlank(message = "Mã môn học không được để trống")
    private String subjectCode;

    @NotBlank(message = "Tên môn học không được để trống")
    private String subjectName;

    private String description;
}