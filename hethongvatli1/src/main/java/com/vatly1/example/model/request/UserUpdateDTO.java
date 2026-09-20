package com.vatly1.example.model.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDTO {

  @Schema(description = "Tên đăng nhập mới (tùy chọn)")
  @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
  private String username;

  @Schema(description = "Email mới (tùy chọn)")
  @Email
  private String email;
}