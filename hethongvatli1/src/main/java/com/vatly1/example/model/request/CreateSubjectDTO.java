package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

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