package com.vatly1.example.model.dto;

import com.vatly1.example.entity.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportItemDTO {
    private int rowNumber;
    private String studentCode;
    private String fullName;
    private String email;
    private String username;
    private String password;
    private LocalDate dateOfBirth;
    private GenderType gender;
    private String phone;
    private String classCode;
    private UUID userId;
    private String status; // SUCCESS, SKIPPED, ERROR
    private String message;
}
