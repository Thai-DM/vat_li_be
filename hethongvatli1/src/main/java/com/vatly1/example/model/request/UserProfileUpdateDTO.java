package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import java.time.LocalDate;
import com.vatly1.example.entity.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDTO {
  private String fullName;
  private String avatarUrl;
  private LocalDate dateOfBirth;
  private GenderType gender;
  private String phone;
  private String studentCode;
  private String bio;
}