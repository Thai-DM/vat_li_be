package com.vatly1.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.response.AuthResponseDTO;
import com.vatly1.example.model.response.UserDataDTO;
import com.vatly1.example.model.response.UserResponseDTO;
import com.vatly1.example.model.request.SigninRequestDTO;
import com.vatly1.example.model.request.RefreshRequestDTO;
import com.vatly1.example.model.request.UserUpdateDTO;
import com.vatly1.example.model.request.ChangePasswordDTO;
import com.vatly1.example.model.dto.UserProfileDTO;
import com.vatly1.example.model.request.UserProfileUpdateDTO;
import com.vatly1.example.model.request.AdminCreateUserDTO;
import com.vatly1.example.model.request.AdminUpdateUserDTO;
import com.vatly1.example.model.request.ForgotPasswordRequestDTO;
import com.vatly1.example.model.request.ResetPasswordRequestDTO;
import com.vatly1.example.model.request.UpdateUserStatusDTO;
import com.vatly1.example.model.response.StudentImportResultDTO;
import com.vatly1.example.service.IStudentExcelService;
import com.vatly1.example.service.IUserService;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users & Authentication", description = "APIs Đăng nhập, Đăng ký, Quên mật khẩu, Refresh Token, Quản lý tài khoản & Hồ sơ")
@RequiredArgsConstructor
public class UserController {

  private final IUserService userService;
  private final IStudentExcelService studentExcelService;

  @PostMapping("/signin")
  @Operation(summary = "Đăng nhập hệ thống (nhận Access/Refresh Token)", description = "Áp dụng cơ chế Rate Limiting để ngăn chặn tấn công dò mật khẩu (brute-force).")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid username/password supplied")})
  public ResponseEntity<ApiResponse<AuthResponseDTO>> login(
      @Parameter(description = "Signin Credentials") @RequestBody @Valid SigninRequestDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.signin(request.getUsername(), request.getPassword())));
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Yêu cầu mã đặt lại mật khẩu qua email", description = "Tạo mã đặt lại mật khẩu sử dụng 1 lần (TTL 15 phút) và gửi email hướng dẫn. Giới hạn 3 lần/phút.")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset email dispatched successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")})
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Parameter(description = "Email details for password recovery") @RequestBody @Valid ForgotPasswordRequestDTO request) {
    userService.processForgotPassword(request.getEmail());
    return ResponseEntity.ok(ApiResponse.success(null, "Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Đặt lại mật khẩu bằng mã xác thực (Token)", description = "Xác thực mã một lần (token) hợp lệ và cập nhật mật khẩu mới cho người dùng.")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid, expired, or previously used token")})
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Parameter(description = "Token and new password details") @RequestBody @Valid ResetPasswordRequestDTO request) {
    userService.processResetPassword(request.getToken(), request.getNewPassword());
    return ResponseEntity.ok(ApiResponse.success(null, "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập với mật khẩu mới."));
  }

  @PostMapping("/admin/create-user")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo người dùng với vai trò cụ thể (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Username is already in use")})
  public ResponseEntity<ApiResponse<UserResponseDTO>> adminCreateUser(@Parameter(description = "Admin Create User") @RequestBody @Valid AdminCreateUserDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.adminCreateUser(request), "User created successfully"));
  }

  @PostMapping("/signup")
  @Operation(summary = "Đăng ký tài khoản Sinh viên mới (gán mặc định ROLE_STUDENT)")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Username is already in use")})
  public ResponseEntity<ApiResponse<AuthResponseDTO>> signup(@Parameter(description = "Signup User") @RequestBody @Valid UserDataDTO user) {
    return ResponseEntity.ok(ApiResponse.success(userService.signup(user)));
  }

  @DeleteMapping(value = "/{username}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xóa người dùng theo tên đăng nhập (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "The user doesn't exist")})
  public ResponseEntity<ApiResponse<String>> delete(@Parameter(description = "Username") @PathVariable String username) {
    userService.delete(username);
    return ResponseEntity.ok(ApiResponse.success(username, "User deleted successfully"));
  }

  @GetMapping(value = "/{username}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tra cứu người dùng theo tên đăng nhập (Chỉ Admin)")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "The user doesn't exist")})
  public ResponseEntity<ApiResponse<UserResponseDTO>> search(@Parameter(description = "Username") @PathVariable String username) {
    return ResponseEntity.ok(ApiResponse.success(userService.search(username)));
  }

  @GetMapping(value = "/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Lấy thông tin tài khoản đang đăng nhập")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Expired or invalid JWT token")})
  public ResponseEntity<ApiResponse<UserResponseDTO>> whoami(HttpServletRequest req) {
    return ResponseEntity.ok(ApiResponse.success(userService.whoami(req)));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Làm mới Access Token bằng Refresh Token",
      description = "Không yêu cầu Access Token. Refresh Token cũ sẽ bị hủy và thay thế bằng cặp token mới (Token Rotation).")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New token pair issued"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Expired or invalid refresh token"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User no longer exists")})
  public ResponseEntity<ApiResponse<AuthResponseDTO>> refresh(@RequestBody @Valid RefreshRequestDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.refresh(request.getRefreshToken())));
  }

  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Đăng xuất (Thu hồi Refresh Token)",
      description = "Hủy bỏ Refresh Token trong hệ thống. Access Token sẽ tự động hết hạn khi tới hạn.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Refresh token revoked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Something went wrong")})
  public ResponseEntity<ApiResponse<Void>> logout(@RequestBody @Valid RefreshRequestDTO request) {
    userService.logout(request.getRefreshToken());
    return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
  }

  @PutMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật tên đăng nhập / email của tôi")
  public ResponseEntity<ApiResponse<UserResponseDTO>> updateUserMe(HttpServletRequest req, @RequestBody @Valid UserUpdateDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.updateUserMe(req, request)));
  }

  @PutMapping("/me/password")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Đổi mật khẩu tài khoản hiện tại")
  public ResponseEntity<ApiResponse<Void>> changePassword(HttpServletRequest req, @RequestBody @Valid ChangePasswordDTO request) {
    userService.changePassword(req, request);
    return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
  }

  @GetMapping("/me/profile")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy hồ sơ cá nhân của tôi")
  public ResponseEntity<ApiResponse<UserProfileDTO>> getMyProfile(HttpServletRequest req) {
    return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(req)));
  }

  @PutMapping("/me/profile")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật hồ sơ cá nhân của tôi")
  public ResponseEntity<ApiResponse<UserProfileDTO>> updateMyProfile(HttpServletRequest req, @RequestBody @Valid UserProfileUpdateDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.updateMyProfile(req, request)));
  }

  @GetMapping("/admin/users")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy danh sách người dùng phân trang (Chỉ Admin)")
  public ResponseEntity<ApiResponse<Page<UserResponseDTO>>> getAllUsers(@ParameterObject Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(pageable)));
  }

  @GetMapping("/admin/users/{id}/profile")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lấy hồ sơ người dùng theo ID (Chỉ Admin)")
  public ResponseEntity<ApiResponse<UserProfileDTO>> getUserProfile(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(id)));
  }

  @PutMapping("/admin/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Cập nhật vai trò / email người dùng (Chỉ Admin)")
  public ResponseEntity<ApiResponse<UserResponseDTO>> adminUpdateUser(@PathVariable UUID id, @RequestBody @Valid AdminUpdateUserDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.adminUpdateUser(id, request)));
  }

  @PutMapping("/admin/users/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Khóa / Mở khóa trạng thái người dùng (Chỉ Admin)")
  public ResponseEntity<ApiResponse<UserResponseDTO>> adminUpdateUserStatus(@PathVariable UUID id, @RequestBody @Valid UpdateUserStatusDTO request) {
    return ResponseEntity.ok(ApiResponse.success(userService.adminUpdateUserStatus(id, request)));
  }

  @GetMapping("/import-excel/template")
  @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
  @Operation(summary = "Tải file mẫu Excel nhập danh sách sinh viên", description = "Tải xuống file Excel (.xlsx) chuẩn hóa để quản trị viên / giảng viên điền danh sách sinh viên cần tạo tài khoản hàng loạt.")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<byte[]> downloadStudentTemplate() {
    byte[] excelBytes = studentExcelService.downloadStudentExcelTemplate();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mau_import_sinh_vien.xlsx")
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(excelBytes);
  }

  @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
  @Operation(summary = "Tạo tài khoản sinh viên hàng loạt từ file Excel", description = "Tải lên tệp Excel (.xlsx, .xls) chứa danh sách sinh viên để tạo hàng loạt tài khoản người dùng và hồ sơ sinh viên. Có thể tự động ghi danh vào lớp học phần.")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<ApiResponse<StudentImportResultDTO>> importStudentsFromExcel(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "defaultPassword", required = false, defaultValue = "Vatly1@123") String defaultPassword,
      @RequestParam(value = "classId", required = false) UUID classId) {
    StudentImportResultDTO result = studentExcelService.importStudentsFromExcel(file, defaultPassword, classId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(result, "Xử lý nhập danh sách sinh viên từ Excel hoàn tất"));
  }
}