package com.vatly1.example.dto.request;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.UserRole;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserDTO {

  private UserRole role;

  @Email
  private String email;
}
