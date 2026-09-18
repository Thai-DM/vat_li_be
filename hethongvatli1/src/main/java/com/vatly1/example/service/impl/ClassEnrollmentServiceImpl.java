package com.vatly1.example.service.impl;

import com.vatly1.example.dto.request.BulkEnrollmentDTO;
import com.vatly1.example.dto.EnrollmentDTO;
import com.vatly1.example.dto.request.SingleEnrollmentDTO;
import com.vatly1.example.dto.request.UpdateEnrollmentStatusDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.converter.ClassEnrollmentConverter;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IClassEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassEnrollmentServiceImpl implements IClassEnrollmentService {

    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassRepository classRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final ClassEnrollmentConverter classEnrollmentConverter;

    @Override
    public Page<EnrollmentDTO> getClassStudents(UUID classId, EnrollmentStatus status, int page, int size, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role != UserRole.ADMIN) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
            boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(classId, currentUserId);
            if (!isOwner && !isAssigned) {
                throw new CustomException("Bạn không có quyền xem danh sách sinh viên của lớp này", HttpStatus.FORBIDDEN);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("enrolledAt").descending());
        Page<ClassEnrollment> enrollments;

        if (status != null) {
            enrollments = enrollmentRepository.findByClassIdAndStatus(classId, status, pageable);
        } else {
            enrollments = enrollmentRepository.findByClassId(classId, pageable);
        }

        return enrollments.map(enrollment -> {
            User user = userRepository.findById(enrollment.getStudentId()).orElse(null);
            UserProfile profile = userProfileRepository.findById(enrollment.getStudentId()).orElse(null);
            return classEnrollmentConverter.toEnrollmentDTO(enrollment, user, profile);
        });
    }

    @Override
    @Transactional
    public void enrollSingleStudent(UUID classId, SingleEnrollmentDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkWriteAccess(clazz, currentUserId, currentUserRole);

        if (clazz.getStatus() != ClassStatus.ACTIVE) {
            throw new CustomException("Chỉ có thể ghi danh vào lớp đang hoạt động (ACTIVE)", HttpStatus.BAD_REQUEST);
        }

        if (clazz.getMaxStudents() != null) {
            int currentCount = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
            if (currentCount >= clazz.getMaxStudents()) {
                throw new CustomException("Lớp học đã đủ số lượng sinh viên tối đa", HttpStatus.BAD_REQUEST);
            }
        }

        enrollStudent(classId, dto.getStudentId());
    }

    @Override
    @Transactional
    public void enrollBulkStudents(UUID classId, BulkEnrollmentDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkWriteAccess(clazz, currentUserId, currentUserRole);

        if (clazz.getStatus() != ClassStatus.ACTIVE) {
            throw new CustomException("Chỉ có thể ghi danh vào lớp đang hoạt động (ACTIVE)", HttpStatus.BAD_REQUEST);
        }

        if (dto.getStudentIds().size() > 500) {
            throw new CustomException("Không thể ghi danh quá 500 sinh viên cùng lúc", HttpStatus.BAD_REQUEST);
        }

        if (clazz.getMaxStudents() != null) {
            int currentCount = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
            if (currentCount + dto.getStudentIds().size() > clazz.getMaxStudents()) {
                throw new CustomException("Vượt quá số lượng sinh viên tối đa của lớp", HttpStatus.BAD_REQUEST);
            }
        }

        for (UUID studentId : dto.getStudentIds()) {
            enrollStudent(classId, studentId);
        }
    }

    @Override
    @Transactional
    public void removeStudent(UUID classId, UUID studentId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkWriteAccess(clazz, currentUserId, currentUserRole);

        if (clazz.getStatus() == ClassStatus.COMPLETED || clazz.getStatus() == ClassStatus.ARCHIVED) {
            throw new CustomException("Không thể xóa sinh viên khỏi lớp đã hoàn thành hoặc lưu trữ", HttpStatus.BAD_REQUEST);
        }

        if (!enrollmentRepository.existsByClassIdAndStudentId(classId, studentId)) {
            throw new CustomException("Sinh viên không tồn tại trong lớp", HttpStatus.BAD_REQUEST);
        }

        enrollmentRepository.deleteByClassIdAndStudentId(classId, studentId);
    }

    @Override
    @Transactional
    public void updateEnrollmentStatus(UUID classId, UUID studentId, UpdateEnrollmentStatusDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkWriteAccess(clazz, currentUserId, currentUserRole);

        ClassEnrollment enrollment = enrollmentRepository.findByClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new CustomException("Sinh viên không tồn tại trong lớp", HttpStatus.NOT_FOUND));

        enrollment.setStatus(dto.getStatus());
        enrollmentRepository.save(enrollment);
    }

    private void enrollStudent(UUID classId, UUID studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new CustomException("Không tìm thấy sinh viên với ID: " + studentId, HttpStatus.NOT_FOUND));

        if (user.getRole() != UserRole.STUDENT) {
            throw new CustomException("Người dùng " + user.getUsername() + " không phải là sinh viên", HttpStatus.BAD_REQUEST);
        }

        if (enrollmentRepository.existsByClassIdAndStudentId(classId, studentId)) {
            // Nếu đã tồn tại, bỏ qua (không quăng lỗi để bulk enroll chạy mượt)
            return;
        }

        ClassEnrollment enrollment = ClassEnrollment.builder()
                .classId(classId)
                .studentId(studentId)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        enrollmentRepository.save(enrollment);
    }

    private void checkWriteAccess(Class clazz, UUID currentUserId, String currentUserRole) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        if (role == UserRole.INSTRUCTOR) {
            if (clazz.getInstructorId() == null || !clazz.getInstructorId().equals(currentUserId)) {
                throw new CustomException("Bạn không phải giảng viên của lớp này", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("Chỉ chủ lớp hoặc Admin mới có quyền sửa đổi danh sách sinh viên", HttpStatus.FORBIDDEN);
        }
    }
}
