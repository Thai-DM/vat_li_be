package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.BulkEnrollmentDTO;
import com.vatly1.example.model.dto.EnrollmentDTO;
import com.vatly1.example.model.request.SingleEnrollmentDTO;
import com.vatly1.example.model.request.UpdateEnrollmentStatusDTO;
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

import java.util.List;
import java.util.Optional;
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

        User user = userRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy sinh viên với ID: " + dto.getStudentId(), HttpStatus.NOT_FOUND));

        if (user.getRole() != UserRole.STUDENT) {
            throw new CustomException("Người dùng " + user.getUsername() + " không phải là sinh viên", HttpStatus.BAD_REQUEST);
        }

        // Kiểm tra xem sinh viên đã từng có bản ghi trong chính lớp này chưa
        Optional<ClassEnrollment> existingInThisClass = enrollmentRepository.findByClassIdAndStudentId(classId, dto.getStudentId());
        if (existingInThisClass.isPresent()) {
            ClassEnrollment currentEnr = existingInThisClass.get();
            if (currentEnr.getStatus() == EnrollmentStatus.ACTIVE) {
                // Đã ở trạng thái ACTIVE trong lớp này, trả về thành công (idempotent)
                return;
            }

            // Trường hợp đăng ký lại vào lớp đã từng DROPPED hoặc COMPLETED:
            // 1. Kiểm tra xem sinh viên có đang ACTIVE ở lớp nào KHÁC cùng môn học và học kỳ không
            validateNoActiveEnrollmentInOtherClass(dto.getStudentId(), clazz.getSubjectId(), clazz.getSemesterId(), classId);

            // 2. Kiểm tra sĩ số tối đa
            checkMaxCapacity(clazz, classId, 1);

            // 3. Tái kích hoạt bản ghi hiện tại thành ACTIVE (tránh vi phạm UNIQUE(class_id, student_id))
            currentEnr.setStatus(EnrollmentStatus.ACTIVE);
            currentEnr.setSubjectId(clazz.getSubjectId());
            currentEnr.setSemesterId(clazz.getSemesterId());
            try {
                enrollmentRepository.save(currentEnr);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new CustomException("Sinh viên đã đăng ký vào một lớp khác của môn học này trong cùng học kỳ", HttpStatus.CONFLICT);
            }
            return;
        }

        // Trường hợp sinh viên chưa từng có bản ghi trong lớp này:
        // 1. Kiểm tra xem sinh viên có đang ACTIVE ở lớp nào KHÁC cùng môn học và học kỳ không
        validateNoActiveEnrollmentInOtherClass(dto.getStudentId(), clazz.getSubjectId(), clazz.getSemesterId(), classId);

        // 2. Kiểm tra sĩ số tối đa
        checkMaxCapacity(clazz, classId, 1);

        // 3. Tạo mới enrollment
        ClassEnrollment enrollment = ClassEnrollment.builder()
                .classId(classId)
                .studentId(dto.getStudentId())
                .subjectId(clazz.getSubjectId())
                .semesterId(clazz.getSemesterId())
                .status(EnrollmentStatus.ACTIVE)
                .build();

        try {
            enrollmentRepository.save(enrollment);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new CustomException("Sinh viên đã đăng ký vào một lớp khác của môn học này trong cùng học kỳ", HttpStatus.CONFLICT);
        }
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

        if (dto.getStudentIds() == null || dto.getStudentIds().isEmpty()) {
            return;
        }

        if (dto.getStudentIds().size() > 500) {
            throw new CustomException("Không thể ghi danh quá 500 sinh viên cùng lúc", HttpStatus.BAD_REQUEST);
        }

        // Sắp xếp danh sách studentId tăng dần để giảm thiểu nguy cơ Deadlock khi có nhiều bulk operation đồng thời
        List<UUID> sortedStudentIds = new java.util.ArrayList<>(dto.getStudentIds());
        sortedStudentIds.sort(UUID::compareTo);

        // Bước 1: Validate toàn bộ sinh viên trong batch trước khi thao tác (All-or-Nothing contract)
        int newlyActivatedCount = 0;
        for (UUID studentId : sortedStudentIds) {
            User user = userRepository.findById(studentId)
                    .orElseThrow(() -> new CustomException("Không tìm thấy sinh viên với ID: " + studentId, HttpStatus.NOT_FOUND));

            if (user.getRole() != UserRole.STUDENT) {
                throw new CustomException("Người dùng " + user.getUsername() + " không phải là sinh viên", HttpStatus.BAD_REQUEST);
            }

            // Kiểm tra trùng môn/kỳ với lớp khác
            validateNoActiveEnrollmentInOtherClass(studentId, clazz.getSubjectId(), clazz.getSemesterId(), classId);

            Optional<ClassEnrollment> existing = enrollmentRepository.findByClassIdAndStudentId(classId, studentId);
            if (existing.isEmpty() || existing.get().getStatus() != EnrollmentStatus.ACTIVE) {
                newlyActivatedCount++;
            }
        }

        // Bước 2: Kiểm tra sĩ số tối đa
        checkMaxCapacity(clazz, classId, newlyActivatedCount);

        // Bước 3: Thực hiện ghi danh hoặc tái kích hoạt
        try {
            for (UUID studentId : sortedStudentIds) {
                Optional<ClassEnrollment> existing = enrollmentRepository.findByClassIdAndStudentId(classId, studentId);
                if (existing.isPresent()) {
                    ClassEnrollment currentEnr = existing.get();
                    if (currentEnr.getStatus() != EnrollmentStatus.ACTIVE) {
                        currentEnr.setStatus(EnrollmentStatus.ACTIVE);
                        currentEnr.setSubjectId(clazz.getSubjectId());
                        currentEnr.setSemesterId(clazz.getSemesterId());
                        enrollmentRepository.save(currentEnr);
                    }
                } else {
                    ClassEnrollment enrollment = ClassEnrollment.builder()
                            .classId(classId)
                            .studentId(studentId)
                            .subjectId(clazz.getSubjectId())
                            .semesterId(clazz.getSemesterId())
                            .status(EnrollmentStatus.ACTIVE)
                            .build();
                    enrollmentRepository.save(enrollment);
                }
            }
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new CustomException("Xung đột dữ liệu: Một hoặc nhiều sinh viên đã đăng ký vào lớp khác của môn học này trong cùng học kỳ", HttpStatus.CONFLICT);
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

        // Nếu kích hoạt lại từ DROPPED hoặc COMPLETED sang ACTIVE:
        if (dto.getStatus() == EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            validateNoActiveEnrollmentInOtherClass(studentId, clazz.getSubjectId(), clazz.getSemesterId(), classId);
            checkMaxCapacity(clazz, classId, 1);
        }

        enrollment.setStatus(dto.getStatus());
        enrollment.setSubjectId(clazz.getSubjectId());
        enrollment.setSemesterId(clazz.getSemesterId());

        try {
            enrollmentRepository.save(enrollment);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new CustomException("Sinh viên đã có đăng ký hoạt động ở một lớp khác của môn học này trong cùng học kỳ", HttpStatus.CONFLICT);
        }
    }

    private void validateNoActiveEnrollmentInOtherClass(UUID studentId, UUID subjectId, UUID semesterId, UUID currentClassId) {
        List<ClassEnrollment> enrollments = enrollmentRepository.findByStudentIdAndSubjectIdAndSemesterId(studentId, subjectId, semesterId);
        Optional<ClassEnrollment> activeOther = enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE && !e.getClassId().equals(currentClassId))
                .findFirst();
        if (activeOther.isPresent()) {
            UUID otherClassId = activeOther.get().getClassId();
            String otherCode = classRepository.findById(otherClassId)
                    .map(Class::getClassCode)
                    .orElse(otherClassId.toString());
            throw new CustomException(
                    String.format("Sinh viên đã đăng ký vào một lớp khác (%s) của môn học này trong học kỳ hiện tại", otherCode),
                    HttpStatus.CONFLICT);
        }
    }

    private void checkMaxCapacity(Class clazz, UUID classId, int needToAddCount) {
        if (clazz.getMaxStudents() != null && needToAddCount > 0) {
            int currentCount = enrollmentRepository.countByClassIdAndStatus(classId, EnrollmentStatus.ACTIVE);
            if (currentCount + needToAddCount > clazz.getMaxStudents()) {
                throw new CustomException("Vượt quá số lượng sinh viên tối đa của lớp (" + clazz.getMaxStudents() + ")", HttpStatus.BAD_REQUEST);
            }
        }
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