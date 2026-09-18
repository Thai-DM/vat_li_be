package com.vatly1.example.model.dto;

import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import com.vatly1.example.entity.enums.ClassStaffRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassStaffDTO {
    private java.util.UUID userId;
    private String username;
    private String fullName;
    private String email;
    private ClassStaffRole roleInClass;
}