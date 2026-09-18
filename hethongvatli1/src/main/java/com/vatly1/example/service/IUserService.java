package com.vatly1.example.service;

import jakarta.servlet.http.HttpServletRequest;
import com.vatly1.example.dto.AuthResponseDTO;
import com.vatly1.example.dto.UserDataDTO;
import com.vatly1.example.dto.UserResponseDTO;
import com.vatly1.example.entity.User;

import com.vatly1.example.dto.AdminCreateUserDTO;
import com.vatly1.example.dto.AdminUpdateUserDTO;
import com.vatly1.example.dto.ChangePasswordDTO;
import com.vatly1.example.dto.UpdateUserStatusDTO;
import com.vatly1.example.dto.UserProfileDTO;
import com.vatly1.example.dto.UserProfileUpdateDTO;
import com.vatly1.example.dto.UserUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface IUserService {
    AuthResponseDTO signin(String username, String password);
    AuthResponseDTO signup(UserDataDTO user);
    UserResponseDTO adminCreateUser(AdminCreateUserDTO adminCreateUserDTO);
    User register(User user);
    void delete(String username);
    UserResponseDTO search(String username);
    UserResponseDTO whoami(HttpServletRequest req);
    AuthResponseDTO refresh(String refreshToken);
    void logout(String refreshToken);
    UserResponseDTO updateUserMe(HttpServletRequest req, UserUpdateDTO updateDTO);
    void changePassword(HttpServletRequest req, ChangePasswordDTO changePasswordDTO);
    UserProfileDTO getMyProfile(HttpServletRequest req);
    UserProfileDTO updateMyProfile(HttpServletRequest req, UserProfileUpdateDTO profileUpdateDTO);
    UserProfileDTO getUserProfile(UUID id);
    Page<UserResponseDTO> getAllUsers(Pageable pageable);
    UserResponseDTO adminUpdateUser(UUID id, AdminUpdateUserDTO updateDTO);
    UserResponseDTO adminUpdateUserStatus(UUID id, UpdateUserStatusDTO updateDTO);
    void processForgotPassword(String email);
    void processResetPassword(String token, String newPassword);
}
