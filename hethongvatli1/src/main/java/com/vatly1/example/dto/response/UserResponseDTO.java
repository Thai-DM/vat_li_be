package com.vatly1.example.dto.response;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

  @Schema
  private UUID userId;
  
  @Schema
  private String username;
  
  @Schema
  private String email;
  
  @Schema
  private UserRole role;

  @Schema
  private UserStatus status;
}