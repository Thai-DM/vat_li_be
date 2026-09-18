package com.vatly1.example.dto.response;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The token pair returned by signin, signup and refresh. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

  @Schema(description = "Short-lived JWT to send as 'Authorization: Bearer <token>'")
  private String accessToken;

  @Schema(description = "Long-lived opaque token used to obtain a new access token")
  private String refreshToken;

  @Schema(description = "Authentication scheme the access token is used with", example = "Bearer")
  private String tokenType;

  @Schema(description = "Access token lifetime in seconds", example = "3600")
  private Long expiresIn;
  
  @Schema(description = "Refresh token lifetime in seconds", example = "604800")
  private Long refreshExpiresIn;
}
