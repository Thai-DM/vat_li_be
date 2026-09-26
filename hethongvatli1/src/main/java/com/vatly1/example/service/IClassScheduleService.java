package com.vatly1.example.service;

import com.vatly1.example.model.dto.ClassScheduleDTO;
import com.vatly1.example.model.request.CreateClassScheduleDTO;
import com.vatly1.example.model.request.UpdateClassScheduleDTO;

import java.util.List;
import java.util.UUID;

public interface IClassScheduleService {

    List<ClassScheduleDTO> getSchedulesByClassId(UUID classId, UUID currentUserId, String currentUserRole);

    List<ClassScheduleDTO> getStudentSchedule(UUID studentId, UUID semesterId, UUID currentUserId, String currentUserRole);

    ClassScheduleDTO createSchedule(UUID classId, CreateClassScheduleDTO dto, UUID currentUserId, String currentUserRole);

    ClassScheduleDTO updateSchedule(UUID scheduleId, UpdateClassScheduleDTO dto, UUID currentUserId, String currentUserRole);

    void deleteSchedule(UUID scheduleId, UUID currentUserId, String currentUserRole);
}
