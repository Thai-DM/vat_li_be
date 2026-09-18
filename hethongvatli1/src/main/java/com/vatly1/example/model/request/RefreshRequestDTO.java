package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of the refresh and logout requests. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequestDTO {

  @Schema(description = "Refresh token used to obtain a new access token")
  @NotBlank
  private String refreshToken;
}