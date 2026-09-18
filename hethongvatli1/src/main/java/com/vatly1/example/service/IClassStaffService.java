package com.vatly1.example.service;

import com.vatly1.example.dto.AssignStaffDTO;
import com.vatly1.example.dto.ClassStaffDTO;

import java.util.List;
import java.util.UUID;

public interface IClassStaffService {
    List<ClassStaffDTO> getClassStaff(UUID classId, UUID currentUserId, String currentUserRole);
    void assignStaff(UUID classId, AssignStaffDTO dto, UUID currentUserId, String currentUserRole);
    void removeStaff(UUID classId, UUID staffUserId, UUID currentUserId, String currentUserRole);
}
