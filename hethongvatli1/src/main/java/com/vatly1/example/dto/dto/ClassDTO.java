package com.vatly1.example.dto.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.ClassStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassDTO {
    private java.util.UUID classId;
    private java.util.UUID subjectId;
    private java.util.UUID semesterId;
    private String classCode;
    private java.util.UUID instructorId;
    private Integer maxStudents;
    private ClassStatus status;
    private java.time.Instant createdAt;
}