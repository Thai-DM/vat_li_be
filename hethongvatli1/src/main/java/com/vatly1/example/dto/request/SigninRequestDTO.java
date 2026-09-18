package com.vatly1.example.dto.request;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigninRequestDTO {

    @Schema(description = "Username for authentication", example = "admin")
    @NotBlank(message = "Username cannot be empty")
    private String username;

    @Schema(description = "Password for authentication", example = "admin123456")
    @NotBlank(message = "Password cannot be empty")
    private String password;
}
