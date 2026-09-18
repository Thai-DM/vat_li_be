package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.vatly1.example.entity.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserDTO {

  @Schema(description = "Username of the new user", example = "newinstructor")
  @NotBlank
  @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
  private String username;

  @Schema(description = "Email of the new user", example = "instructor@example.com")
  @NotBlank
  @Email
  private String email;

  @Schema(description = "Password of the new user", example = "password123")
  @NotBlank
  @Size(min = 8, message = "Minimum password length: 8 characters")
  private String password;

  @Schema(description = "Role assigned by Admin", example = "INSTRUCTOR")
  @NotNull(message = "Role is required")
  private UserRole role;
}