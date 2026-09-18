package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import com.vatly1.example.entity.enums.ClassStaffRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignStaffDTO {
    @NotNull(message = "Người dùng không được để trống")
    private java.util.UUID userId;

    @NotNull(message = "Vai trò trong lớp không được để trống")
    private ClassStaffRole roleInClass;
}