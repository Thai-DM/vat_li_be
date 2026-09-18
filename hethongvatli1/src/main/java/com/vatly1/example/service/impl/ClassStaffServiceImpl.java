package com.vatly1.example.service.impl;

import com.vatly1.example.dto.AssignStaffDTO;
import com.vatly1.example.dto.ClassStaffDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassStaff;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.converter.ClassStaffConverter;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IClassStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassStaffServiceImpl implements IClassStaffService {

    private final IClassStaffRepository classStaffRepository;
    private final IClassRepository classRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final ClassStaffConverter classStaffConverter;

    @Override
    public List<ClassStaffDTO> getClassStaff(UUID classId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role != UserRole.ADMIN) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
            boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(classId, currentUserId);
            if (!isOwner && !isAssigned) {
                throw new CustomException("Bạn không có quyền xem nhân sự của lớp này", HttpStatus.FORBIDDEN);
            }
        }

        List<ClassStaff> staffs = classStaffRepository.findByClassId(classId);
        List<ClassStaffDTO> dtos = new ArrayList<>();
        
        for (ClassStaff staff : staffs) {
            User user = userRepository.findById(staff.getUserId()).orElse(null);
            UserProfile profile = userProfileRepository.findById(staff.getUserId()).orElse(null);
            
            if (user != null) {
                dtos.add(classStaffConverter.toClassStaffDTO(staff, user, profile));
            }
        }
        
        return dtos;
    }

    @Override
    @Transactional
    public void assignStaff(UUID classId, AssignStaffDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkOwnership(clazz, currentUserId, currentUserRole);

        User targetUser = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new CustomException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        if (targetUser.getRole() == UserRole.STUDENT) {
            throw new CustomException("Không thể phân công sinh viên làm giảng viên/trợ giảng", HttpStatus.BAD_REQUEST);
        }

        if (classStaffRepository.existsByClassIdAndUserId(classId, dto.getUserId())) {
            throw new CustomException("Người dùng đã được phân công vào lớp này", HttpStatus.BAD_REQUEST);
        }

        ClassStaff classStaff = ClassStaff.builder()
                .classId(classId)
                .userId(dto.getUserId())
                .roleInClass(dto.getRoleInClass())
                .build();

        classStaffRepository.save(classStaff);
    }

    @Override
    @Transactional
    public void removeStaff(UUID classId, UUID staffUserId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkOwnership(clazz, currentUserId, currentUserRole);

        if (!classStaffRepository.existsByClassIdAndUserId(classId, staffUserId)) {
            throw new CustomException("Người dùng không nằm trong danh sách nhân sự của lớp", HttpStatus.BAD_REQUEST);
        }

        classStaffRepository.deleteByClassIdAndUserId(classId, staffUserId);
    }

    private void checkOwnership(Class clazz, UUID currentUserId, String currentUserRole) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        if (role == UserRole.INSTRUCTOR) {
            if (clazz.getInstructorId() == null || !clazz.getInstructorId().equals(currentUserId)) {
                throw new CustomException("Bạn không phải chủ lớp, không thể phân công nhân sự", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("Chỉ chủ lớp hoặc Admin mới có quyền quản lý nhân sự", HttpStatus.FORBIDDEN);
        }
    }
}
