package com.vatly1.example.service.impl;

import com.vatly1.example.dto.dto.ClassDTO;
import com.vatly1.example.dto.request.CreateClassDTO;
import com.vatly1.example.dto.request.UpdateClassDTO;
import com.vatly1.example.dto.request.UpdateClassStatusDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.converter.ClassConverter;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.service.IClassService;
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
public class ClassServiceImpl implements IClassService {

    private final IClassRepository classRepository;
    private final ISubjectRepository subjectRepository;
    private final ISemesterRepository semesterRepository;
    private final IClassStaffRepository classStaffRepository;
    private final ClassConverter classConverter;

    @Override
    public Page<ClassDTO> getClasses(UUID subjectId, UUID semesterId, ClassStatus status, int page, int size, UUID currentUserId, String currentUserRole) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Class> classes;

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());

        if (role == UserRole.ADMIN) {
            if (subjectId != null && semesterId != null && status != null) {
                classes = classRepository.findBySubjectIdAndSemesterIdAndStatus(subjectId, semesterId, status, pageable);
            } else if (subjectId != null && semesterId != null) {
                classes = classRepository.findBySubjectIdAndSemesterId(subjectId, semesterId, pageable);
            } else if (subjectId != null && status != null) {
                classes = classRepository.findBySubjectIdAndStatus(subjectId, status, pageable);
            } else if (semesterId != null && status != null) {
                classes = classRepository.findBySemesterIdAndStatus(semesterId, status, pageable);
            } else if (subjectId != null) {
                classes = classRepository.findBySubjectId(subjectId, pageable);
            } else if (semesterId != null) {
                classes = classRepository.findBySemesterId(semesterId, pageable);
            } else if (status != null) {
                classes = classRepository.findByStatus(status, pageable);
            } else {
                classes = classRepository.findAll(pageable);
            }
        } else if (role == UserRole.INSTRUCTOR) {
            classes = classRepository.findByInstructorIdOrStaffUserId(currentUserId, pageable);
        } else if (role == UserRole.TA) {
            classes = classRepository.findByStaffUserId(currentUserId, pageable);
        } else {
            throw new CustomException("Không có quyền truy cập", HttpStatus.FORBIDDEN);
        }

        return classes.map(classConverter::toClassDTO);
    }

    @Override
    public ClassDTO getClassById(UUID id, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        
        if (role != UserRole.ADMIN) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
            boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(id, currentUserId);
            
            if (!isOwner && !isAssigned) {
                throw new CustomException("Bạn không có quyền truy cập lớp học này", HttpStatus.FORBIDDEN);
            }
        }

        return classConverter.toClassDTO(clazz);
    }

    @Override
    @Transactional
    public ClassDTO createClass(CreateClassDTO dto, UUID instructorId) {
        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Môn học không tồn tại", HttpStatus.BAD_REQUEST);
        }
        if (!semesterRepository.existsById(dto.getSemesterId())) {
            throw new CustomException("Học kỳ không tồn tại", HttpStatus.BAD_REQUEST);
        }

        Class clazz = classConverter.toClass(dto);
        clazz.setInstructorId(instructorId);

        clazz = classRepository.save(clazz);
        return classConverter.toClassDTO(clazz);
    }

    @Override
    @Transactional
    public ClassDTO updateClass(UUID id, UpdateClassDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkClassOwnership(clazz, currentUserId, currentUserRole);

        if (clazz.getStatus() == ClassStatus.COMPLETED || clazz.getStatus() == ClassStatus.ARCHIVED) {
            throw new CustomException("Không thể sửa lớp học đã hoàn thành hoặc lưu trữ", HttpStatus.BAD_REQUEST);
        }

        clazz.setClassCode(dto.getClassCode());
        clazz.setMaxStudents(dto.getMaxStudents());

        clazz = classRepository.save(clazz);
        return classConverter.toClassDTO(clazz);
    }

    @Override
    @Transactional
    public ClassDTO updateClassStatus(UUID id, UpdateClassStatusDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        checkClassOwnership(clazz, currentUserId, currentUserRole);

        ClassStatus currentStatus = clazz.getStatus();
        ClassStatus newStatus = dto.getStatus();

        if (currentStatus == ClassStatus.DRAFT && newStatus != ClassStatus.ACTIVE) {
            throw new CustomException("Trạng thái hợp lệ tiếp theo là ACTIVE", HttpStatus.BAD_REQUEST);
        } else if (currentStatus == ClassStatus.ACTIVE && newStatus != ClassStatus.COMPLETED) {
            throw new CustomException("Trạng thái hợp lệ tiếp theo là COMPLETED", HttpStatus.BAD_REQUEST);
        } else if (currentStatus == ClassStatus.COMPLETED && newStatus != ClassStatus.ARCHIVED) {
            throw new CustomException("Trạng thái hợp lệ tiếp theo là ARCHIVED", HttpStatus.BAD_REQUEST);
        } else if (currentStatus == ClassStatus.ARCHIVED) {
            throw new CustomException("Không thể đổi trạng thái của lớp đã ARCHIVED", HttpStatus.BAD_REQUEST);
        }

        clazz.setStatus(newStatus);
        clazz = classRepository.save(clazz);
        return classConverter.toClassDTO(clazz);
    }

    private void checkClassOwnership(Class clazz, UUID currentUserId, String currentUserRole) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        if (role == UserRole.INSTRUCTOR) {
            if (clazz.getInstructorId() == null || !clazz.getInstructorId().equals(currentUserId)) {
                throw new CustomException("Bạn không phải giảng viên của lớp này", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("Bạn không có quyền thay đổi thông tin lớp học", HttpStatus.FORBIDDEN);
        }
    }
    @Override
    public Page<ClassDTO> getMyClasses(UUID studentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Class> classes = classRepository.findByStudentId(studentId, pageable);
        return classes.map(classConverter::toClassDTO);
    }
}