package com.vatly1.example.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import java.time.LocalDate;
import com.vatly1.example.entity.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
  private String fullName;
  private String avatarUrl;
  private LocalDate dateOfBirth;
  private GenderType gender;
  private String phone;
  private String studentCode;
  private String bio;
}
