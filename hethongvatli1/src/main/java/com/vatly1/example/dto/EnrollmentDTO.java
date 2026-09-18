package com.vatly1.example.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentDTO {
    private java.util.UUID enrollmentId;
    private java.util.UUID studentId;
    private String username;
    private String fullName;
    private String studentCode;
    private java.time.Instant enrolledAt;
    private EnrollmentStatus status;
}
