package com.vatly1.example.controller;



import com.vatly1.example.model.request.SigninRequestDTO;import com.vatly1.example.model.request.AdminCreateUserDTO;import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.vatly1.example.model.response.AuthResponseDTO;
import com.vatly1.example.model.request.RefreshRequestDTO;
import com.vatly1.example.model.response.UserDataDTO;
import com.vatly1.example.model.response.UserResponseDTO;
import com.vatly1.example.model.request.UserUpdateDTO;
import com.vatly1.example.model.request.ChangePasswordDTO;
import com.vatly1.example.model.dto.UserProfileDTO;
import com.vatly1.example.model.request.UserProfileUpdateDTO;
import com.vatly1.example.model.request.AdminUpdateUserDTO;
import com.vatly1.example.model.request.ForgotPasswordRequestDTO;
import com.vatly1.example.model.request.ResetPasswordRequestDTO;
import com.vatly1.example.model.request.UpdateUserStatusDTO;
import com.vatly1.example.service.IUserService;
// import com.vatly1.example.model.response.ApiResponse; // Replaced with fully qualified name
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users & Authentication", description = "APIs Đăng nhập, Đăng ký, Quên mật khẩu, Refresh Token, Quản lý tài khoản & Hồ sơ")
@RequiredArgsConstructor
public class UserController {

  private final IUserService userService;

  @PostMapping("/signin")
  @Operation(summary = "Đăng nhập hệ thống (nhận Access/Refresh Token)", description = "Áp dụng cơ chế Rate Limiting để ngăn chặn tấn công dò mật khẩu (brute-force).")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "422", description = "Invalid username/password supplied")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<AuthResponseDTO>> login(
      @Parameter(description = "Signin Credentials") @RequestBody @Valid com.vatly1.example.model.request.SigninRequestDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.signin(request.getUsername(), request.getPassword())));
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Yêu cầu mã đặt lại mật khẩu qua email", description = "Tạo mã đặt lại mật khẩu sử dụng 1 lần (TTL 15 phút) và gửi email hướng dẫn. Giới hạn 3 lần/phút.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Password reset email dispatched successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid email format"),
      @ApiResponse(responseCode = "429", description = "Rate limit exceeded")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<Void>> forgotPassword(
      @Parameter(description = "Email details for password recovery") @RequestBody @Valid ForgotPasswordRequestDTO request) {
    userService.processForgotPassword(request.getEmail());
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(null, "Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Đặt lại mật khẩu bằng mã xác thực (Token)", description = "Xác thực mã một lần (token) hợp lệ và cập nhật mật khẩu mới cho người dùng.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Password reset successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid, expired, or previously used token")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<Void>> resetPassword(
      @Parameter(description = "Token and new password details") @RequestBody @Valid ResetPasswordRequestDTO request) {
    userService.processResetPassword(request.getToken(), request.getNewPassword());
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(null, "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập với mật khẩu mới."));
  }

  @PostMapping("/admin/create-user")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo người dùng với vai trò cụ thể (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "403", description = "Access denied"),
      @ApiResponse(responseCode = "422", description = "Username is already in use")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> adminCreateUser(@Parameter(description = "Admin Create User") @RequestBody @Valid com.vatly1.example.model.request.AdminCreateUserDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.adminCreateUser(request), "User created successfully"));
  }

  @PostMapping("/signup")
  @Operation(summary = "Đăng ký tài khoản Sinh viên mới (gán mặc định ROLE_STUDENT)")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "422", description = "Username is already in use")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<AuthResponseDTO>> signup(@Parameter(description = "Signup User") @RequestBody @Valid UserDataDTO user) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.signup(user)));
  }

  @DeleteMapping(value = "/{username}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xóa người dùng theo tên đăng nhập (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "403", description = "Access denied"),
      @ApiResponse(responseCode = "404", description = "The user doesn't exist")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<String>> delete(@Parameter(description = "Username") @PathVariable String username) {
    userService.delete(username);
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(username, "User deleted successfully"));
  }

  @GetMapping(value = "/{username}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tra cứu người dùng theo tên đăng nhập (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "403", description = "Access denied"),
      @ApiResponse(responseCode = "404", description = "The user doesn't exist")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> search(@Parameter(description = "Username") @PathVariable String username) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.search(username)));
  }

  @GetMapping(value = "/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Lấy thông tin tài khoản đang đăng nhập")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Success"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "401", description = "Expired or invalid JWT token")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> whoami(HttpServletRequest req) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.whoami(req)));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Làm mới Access Token bằng Refresh Token",
      description = "Không yêu cầu Access Token. Refresh Token cũ sẽ bị hủy và thay thế bằng cặp token mới (Token Rotation).")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "New token pair issued"),
      @ApiResponse(responseCode = "400", description = "Something went wrong"),
      @ApiResponse(responseCode = "401", description = "Expired or invalid refresh token"),
      @ApiResponse(responseCode = "404", description = "User no longer exists")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<AuthResponseDTO>> refresh(@RequestBody @Valid RefreshRequestDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.refresh(request.getRefreshToken())));
  }

  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Đăng xuất (Thu hồi Refresh Token)",
      description = "Hủy bỏ Refresh Token trong hệ thống. Access Token sẽ tự động hết hạn khi tới hạn.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Refresh token revoked"),
      @ApiResponse(responseCode = "400", description = "Something went wrong")})
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<Void>> logout(@RequestBody @Valid RefreshRequestDTO request) {
    userService.logout(request.getRefreshToken());
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(null, "Logged out successfully"));
  }

  @org.springframework.web.bind.annotation.PutMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật tên đăng nhập / email của tôi")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> updateUserMe(HttpServletRequest req, @RequestBody @Valid UserUpdateDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.updateUserMe(req, request)));
  }

  @PutMapping("/me/password")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Đổi mật khẩu tài khoản hiện tại")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<Void>> changePassword(HttpServletRequest req, @RequestBody @Valid ChangePasswordDTO request) {
    userService.changePassword(req, request);
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(null, "Password changed successfully"));
  }

  @GetMapping("/me/profile")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy hồ sơ cá nhân của tôi")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserProfileDTO>> getMyProfile(HttpServletRequest req) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.getMyProfile(req)));
  }

  @PutMapping("/me/profile")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật hồ sơ cá nhân của tôi")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserProfileDTO>> updateMyProfile(HttpServletRequest req, @RequestBody @Valid UserProfileUpdateDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.updateMyProfile(req, request)));
  }

  @GetMapping("/admin/users")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy danh sách người dùng phân trang (Chỉ Admin)")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<Page<UserResponseDTO>>> getAllUsers(@ParameterObject Pageable pageable) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.getAllUsers(pageable)));
  }

  @GetMapping("/admin/users/{id}/profile")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy hồ sơ người dùng theo ID (Chỉ Admin)")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserProfileDTO>> getUserProfile(@PathVariable UUID id) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.getUserProfile(id)));
  }

  @PutMapping("/admin/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật vai trò / email người dùng (Chỉ Admin)")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> adminUpdateUser(@PathVariable UUID id, @RequestBody @Valid AdminUpdateUserDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.adminUpdateUser(id, request)));
  }

  @PutMapping("/admin/users/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Khóa / Mở khóa trạng thái người dùng (Chỉ Admin)")
  public ResponseEntity<com.vatly1.example.model.response.ApiResponse<UserResponseDTO>> adminUpdateUserStatus(@PathVariable UUID id, @RequestBody @Valid UpdateUserStatusDTO request) {
    return ResponseEntity.ok(com.vatly1.example.model.response.ApiResponse.success(userService.adminUpdateUserStatus(id, request)));
  }
}