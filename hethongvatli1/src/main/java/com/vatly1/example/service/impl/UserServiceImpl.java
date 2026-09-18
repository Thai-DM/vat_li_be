package com.vatly1.example.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.vatly1.example.model.response.AuthResponseDTO;
import com.vatly1.example.model.response.UserDataDTO;
import com.vatly1.example.model.response.UserResponseDTO;
import com.vatly1.example.model.request.AdminCreateUserDTO;
import com.vatly1.example.model.request.AdminUpdateUserDTO;
import com.vatly1.example.model.request.ChangePasswordDTO;
import com.vatly1.example.model.request.UpdateUserStatusDTO;
import com.vatly1.example.model.dto.UserProfileDTO;
import com.vatly1.example.model.request.UserProfileUpdateDTO;
import com.vatly1.example.model.request.UserUpdateDTO;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.repository.IUserProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IUserService;
import com.vatly1.example.service.IRefreshTokenService;
import com.vatly1.example.utils.JwtTokenUtils;
import com.vatly1.example.converter.UserConverter;
import com.vatly1.example.entity.PasswordResetToken;
import com.vatly1.example.repository.IPasswordResetTokenRepository;
import com.vatly1.example.service.IEmailService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

  private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

  private static final String TOKEN_TYPE = "Bearer";

  private final IUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenUtils jwtTokenUtils;
  private final AuthenticationManager authenticationManager;
  private final IRefreshTokenService refreshTokenService;
  private final UserConverter userConverter;
  private final IUserProfileRepository userProfileRepository;
  private final IPasswordResetTokenRepository passwordResetTokenRepository;
  private final IEmailService emailService;
  @Override
  public AuthResponseDTO signin(String username, String password) {
    try {
      authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
      log.info("User signed in: {}", username);
      return issueTokens(userRepository.findByUsername(username));
    } catch (AuthenticationException e) {
      throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  @Override
  public AuthResponseDTO signup(UserDataDTO userDataDTO) {
    User user = userConverter.toUser(userDataDTO);
    user.setRole(UserRole.STUDENT); // Force STUDENT role for public signup
    User created = register(user);
    return issueTokens(created);
  }

  @Override
  public UserResponseDTO adminCreateUser(AdminCreateUserDTO dto) {
    User user = new User();
    user.setUsername(dto.getUsername());
    user.setEmail(dto.getEmail());
    user.setPasswordHash(dto.getPassword());
    user.setRole(dto.getRole());
    User created = register(user);
    return userConverter.toUserResponseDTO(created);
  }

  @Override
  public User register(User user) {
    if (userRepository.existsByUsername(user.getUsername())) {
      throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
    if (user.getStatus() == null) {
      user.setStatus(UserStatus.ACTIVE);
    }
    userRepository.save(user);
    log.info("User registered: {}", user.getUsername());
    return user;
  }

  @Override
  @Transactional
  public void delete(String username) {
    refreshTokenService.deleteAllForUser(username);
    userRepository.deleteByUsername(username);
  }

  @Override
  public UserResponseDTO search(String username) {
    User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new CustomException("The user doesn't exist", HttpStatus.NOT_FOUND);
    }
    return userConverter.toUserResponseDTO(user);
  }

  @Override
  public UserResponseDTO whoami(HttpServletRequest req) {
    String token = jwtTokenUtils.resolveToken(req);
    if (token == null) {
      throw new CustomException("Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
    }
    User user = userRepository.findByUsername(jwtTokenUtils.getUsername(token));
    if (user == null) {
      throw new CustomException("The user doesn't exist", HttpStatus.NOT_FOUND);
    }
    return userConverter.toUserResponseDTO(user);
  }

  @Override
  public AuthResponseDTO refresh(String refreshToken) {
    IRefreshTokenService.RefreshTokenServiceRotation rotation = refreshTokenService.rotate(refreshToken);

    User user = userRepository.findByUsername(rotation.username());
    if (user == null) {
      throw new CustomException("The user doesn't exist", HttpStatus.NOT_FOUND);
    }

    String accessToken = jwtTokenUtils.createToken(user.getUsername(), user.getRole(), user.getUserId());
    log.info("Refreshed tokens for user: {}", user.getUsername());
    return new AuthResponseDTO(accessToken, rotation.newRefreshToken(), TOKEN_TYPE, jwtTokenUtils.getValidityInSeconds(), jwtTokenUtils.getRefreshValidityInSeconds());
  }

  @Override
  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  @Override
  @Transactional
  public UserResponseDTO updateUserMe(HttpServletRequest req, UserUpdateDTO updateDTO) {
    String username = jwtTokenUtils.getUsername(jwtTokenUtils.resolveToken(req));
    User user = userRepository.findByUsername(username);
    if (updateDTO.getUsername() != null && !updateDTO.getUsername().equals(user.getUsername())) {
        if (userRepository.existsByUsername(updateDTO.getUsername())) {
            throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        user.setUsername(updateDTO.getUsername());
    }
    if (updateDTO.getEmail() != null) {
        user.setEmail(updateDTO.getEmail());
    }
    userRepository.save(user);
    return userConverter.toUserResponseDTO(user);
  }

  @Override
  @Transactional
  public void changePassword(HttpServletRequest req, ChangePasswordDTO changePasswordDTO) {
    String username = jwtTokenUtils.getUsername(jwtTokenUtils.resolveToken(req));
    User user = userRepository.findByUsername(username);
    if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPasswordHash())) {
        throw new CustomException("Invalid old password", HttpStatus.BAD_REQUEST);
    }
    user.setPasswordHash(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
    userRepository.save(user);
  }

  @Override
  public UserProfileDTO getMyProfile(HttpServletRequest req) {
    String username = jwtTokenUtils.getUsername(jwtTokenUtils.resolveToken(req));
    User user = userRepository.findByUsername(username);
    return getUserProfile(user.getUserId());
  }

  @Override
  @Transactional
  public UserProfileDTO updateMyProfile(HttpServletRequest req, UserProfileUpdateDTO profileUpdateDTO) {
    String username = jwtTokenUtils.getUsername(jwtTokenUtils.resolveToken(req));
    User user = userRepository.findByUsername(username);
    
    UserProfile profile = userProfileRepository.findById(user.getUserId())
        .orElse(UserProfile.builder().userId(user.getUserId()).build());
        
    profile.setFullName(profileUpdateDTO.getFullName());
    profile.setAvatarUrl(profileUpdateDTO.getAvatarUrl());
    profile.setDateOfBirth(profileUpdateDTO.getDateOfBirth());
    profile.setGender(profileUpdateDTO.getGender());
    profile.setPhone(profileUpdateDTO.getPhone());
    profile.setStudentCode(profileUpdateDTO.getStudentCode());
    profile.setBio(profileUpdateDTO.getBio());
    profile.setUpdatedAt(java.time.Instant.now());
    
    userProfileRepository.save(profile);
    
    return UserProfileDTO.builder()
        .fullName(profile.getFullName())
        .avatarUrl(profile.getAvatarUrl())
        .dateOfBirth(profile.getDateOfBirth())
        .gender(profile.getGender())
        .phone(profile.getPhone())
        .studentCode(profile.getStudentCode())
        .bio(profile.getBio())
        .build();
  }

  @Override
  public UserProfileDTO getUserProfile(UUID id) {
    UserProfile profile = userProfileRepository.findById(id).orElse(null);
    if (profile == null) {
        return new UserProfileDTO(); // empty profile
    }
    return UserProfileDTO.builder()
        .fullName(profile.getFullName())
        .avatarUrl(profile.getAvatarUrl())
        .dateOfBirth(profile.getDateOfBirth())
        .gender(profile.getGender())
        .phone(profile.getPhone())
        .studentCode(profile.getStudentCode())
        .bio(profile.getBio())
        .build();
  }

  @Override
  public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable).map(userConverter::toUserResponseDTO);
  }

  @Override
  @Transactional
  public UserResponseDTO adminUpdateUser(UUID id, AdminUpdateUserDTO updateDTO) {
    User user = userRepository.findById(id).orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
    if (updateDTO.getRole() != null) {
        user.setRole(updateDTO.getRole());
    }
    if (updateDTO.getEmail() != null) {
        user.setEmail(updateDTO.getEmail());
    }
    userRepository.save(user);
    return userConverter.toUserResponseDTO(user);
  }

  @Override
  @Transactional
  public UserResponseDTO adminUpdateUserStatus(UUID id, UpdateUserStatusDTO updateDTO) {
    User user = userRepository.findById(id).orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
    user.setStatus(updateDTO.getStatus());
    userRepository.save(user);
    return userConverter.toUserResponseDTO(user);
  }

  @Override
  @Transactional
  public void processForgotPassword(String email) {
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) {
      log.warn("Forgot password requested for non-existing email: {}", email);
      return;
    }

    User user = userOpt.get();
    passwordResetTokenRepository.deleteByUserId(user.getUserId());

    String token = UUID.randomUUID().toString();
    Instant expiryDate = Instant.now().plus(15, ChronoUnit.MINUTES);

    PasswordResetToken resetToken = PasswordResetToken.builder()
        .token(token)
        .userId(user.getUserId())
        .email(user.getEmail())
        .expiryDate(expiryDate)
        .used(false)
        .build();

    passwordResetTokenRepository.save(resetToken);
    emailService.sendPasswordResetEmail(user.getEmail(), token);
    log.info("Password reset token generated and email dispatched for user: {}", user.getUsername());
  }

  @Override
  @Transactional
  public void processResetPassword(String token, String newPassword) {
    PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
        .orElseThrow(() -> new CustomException("Mã token không hợp lệ hoặc không tồn tại", HttpStatus.BAD_REQUEST));

    if (resetToken.isUsed()) {
      throw new CustomException("Mã token này đã được sử dụng", HttpStatus.BAD_REQUEST);
    }

    if (resetToken.getExpiryDate().isBefore(Instant.now())) {
      throw new CustomException("Mã token đã hết hạn. Vui lòng gửi lại yêu cầu mới", HttpStatus.BAD_REQUEST);
    }

    User user = userRepository.findById(resetToken.getUserId())
        .orElseThrow(() -> new CustomException("Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    resetToken.setUsed(true);
    passwordResetTokenRepository.save(resetToken);

    log.info("Password reset successfully for user: {}", user.getUsername());
  }

  private AuthResponseDTO issueTokens(User user) {
    String accessToken = jwtTokenUtils.createToken(user.getUsername(), user.getRole(), user.getUserId());
    String refreshToken = refreshTokenService.issue(user.getUsername());
    return new AuthResponseDTO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenUtils.getValidityInSeconds(), jwtTokenUtils.getRefreshValidityInSeconds());
  }
}