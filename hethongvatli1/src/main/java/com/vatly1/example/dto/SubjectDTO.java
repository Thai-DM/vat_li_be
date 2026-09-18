package com.vatly1.example.dto;

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
