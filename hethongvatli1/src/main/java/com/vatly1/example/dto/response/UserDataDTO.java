package com.vatly1.example.dto.response;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.vatly1.example.entity.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDataDTO {

  @Schema
  @NotBlank
  @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
  private String username;

  @Schema
  @NotBlank
  @Email
  private String email;

  @Schema
  @NotBlank
  @Size(min = 8, message = "Minimum password length: 8 characters")
  private String password;
}