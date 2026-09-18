package com.vatly1.example.dto.request;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordDTO {

  @Schema(description = "Mật khẩu cũ", required = true)
  @NotBlank(message = "Old password is required")
  private String oldPassword;

  @Schema(description = "Mật khẩu mới", required = true)
  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "Minimum password length: 8 characters")
  private String newPassword;
}
