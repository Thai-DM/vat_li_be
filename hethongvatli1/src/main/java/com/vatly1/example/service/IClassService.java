package com.vatly1.example.service;

import com.vatly1.example.dto.ClassDTO;
import com.vatly1.example.dto.CreateClassDTO;
import com.vatly1.example.dto.UpdateClassDTO;
import com.vatly1.example.dto.UpdateClassStatusDTO;
import com.vatly1.example.entity.enums.ClassStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface IClassService {
    Page<ClassDTO> getClasses(UUID subjectId, UUID semesterId, ClassStatus status, int page, int size, UUID currentUserId, String currentUserRole);
    ClassDTO getClassById(UUID id, UUID currentUserId, String currentUserRole);
    ClassDTO createClass(CreateClassDTO dto, UUID instructorId);
    ClassDTO updateClass(UUID id, UpdateClassDTO dto, UUID currentUserId, String currentUserRole);
    ClassDTO updateClassStatus(UUID id, UpdateClassStatusDTO dto, UUID currentUserId, String currentUserRole);
    Page<ClassDTO> getMyClasses(UUID studentId, int page, int size);
}
