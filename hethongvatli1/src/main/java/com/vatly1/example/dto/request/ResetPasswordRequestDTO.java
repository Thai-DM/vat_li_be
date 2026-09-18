package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu đặt lại mật khẩu mới bằng token xác thực")
public class ResetPasswordRequestDTO {

    @NotBlank(message = "Mã token xác thực không được để trống")
    @Schema(description = "Mã token xác thực nhận được qua email (hiệu lực 15 phút)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String token;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")
    @Schema(description = "Mật khẩu mới của tài khoản", example = "newStrongPass123")
    private String newPassword;
}