package com.vatly1.example.converter;

import org.springframework.stereotype.Component;

import com.vatly1.example.dto.response.UserDataDTO;
import com.vatly1.example.dto.response.UserResponseDTO;
import com.vatly1.example.entity.User;

@Component
public class UserConverter {

    public User toUser(UserDataDTO dto) {
        if (dto == null) {
            return null;
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(dto.getPassword());
        return user;
    }

    public UserResponseDTO toUserResponseDTO(User user) {
        if (user == null) {
            return null;
        }
        UserResponseDTO dto = new UserResponseDTO();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
