package com.vatly1.example.converter;

import com.vatly1.example.model.dto.ClassStaffDTO;
import com.vatly1.example.entity.ClassStaff;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class ClassStaffConverter {

    public ClassStaffDTO toClassStaffDTO(ClassStaff staff, User user, UserProfile profile) {
        if (staff == null) {
            return null;
        }
        return ClassStaffDTO.builder()
                .userId(staff.getUserId())
                .username(user != null ? user.getUsername() : null)
                .email(user != null ? user.getEmail() : null)
                .fullName(profile != null ? profile.getFullName() : null)
                .roleInClass(staff.getRoleInClass())
                .build();
    }
}