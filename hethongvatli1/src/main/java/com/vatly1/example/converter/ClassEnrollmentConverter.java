package com.vatly1.example.converter;

import com.vatly1.example.dto.EnrollmentDTO;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class ClassEnrollmentConverter {

    public EnrollmentDTO toEnrollmentDTO(ClassEnrollment enrollment, User user, UserProfile profile) {
        if (enrollment == null) {
            return null;
        }
        return EnrollmentDTO.builder()
                .enrollmentId(enrollment.getEnrollmentId())
                .studentId(enrollment.getStudentId())
                .username(user != null ? user.getUsername() : null)
                .fullName(profile != null ? profile.getFullName() : null)
                .studentCode(profile != null ? profile.getStudentCode() : null)
                .enrolledAt(enrollment.getEnrolledAt())
                .status(enrollment.getStatus())
                .build();
    }
}
