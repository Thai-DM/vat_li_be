package com.vatly1.example.dto.dto;

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
public class SubjectDTO {
    private java.util.UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private String description;
    private Boolean isActive;
    private java.time.Instant createdAt;
}