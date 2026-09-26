package com.vatly1.example.service.impl;

import com.vatly1.example.converter.ClassScheduleConverter;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.ClassSchedule;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ClassScheduleDTO;
import com.vatly1.example.model.request.CreateClassScheduleDTO;
import com.vatly1.example.model.request.UpdateClassScheduleDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassScheduleRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IClassScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassScheduleServiceImpl implements IClassScheduleService {

    private final IClassScheduleRepository scheduleRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassStaffRepository classStaffRepository;
    private final ISubjectRepository subjectRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final ClassScheduleConverter scheduleConverter;

    @Override
    public List<ClassScheduleDTO> getSchedulesByClassId(UUID classId, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role != UserRole.ADMIN) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
            boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(classId, currentUserId);
            boolean isEnrolled = enrollmentRepository.existsByClassIdAndStudentId(classId, currentUserId);

            if (!isOwner && !isAssigned && !isEnrolled) {
                throw new CustomException("Bạn không có quyền xem lịch học của lớp này", HttpStatus.FORBIDDEN);
            }
        }

        List<ClassSchedule> schedules = scheduleRepository.findByClassIdOrderByDayOfWeekAscStartTimeAsc(classId);
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        Subject subject = clazz.getSubjectId() != null ? subjectRepository.findById(clazz.getSubjectId()).orElse(null) : null;
        User instructor = clazz.getInstructorId() != null ? userRepository.findById(clazz.getInstructorId()).orElse(null) : null;
        UserProfile instructorProfile = clazz.getInstructorId() != null ? userProfileRepository.findById(clazz.getInstructorId()).orElse(null) : null;

        return schedules.stream()
                .map(s -> scheduleConverter.toDTO(s, clazz, subject, instructor, instructorProfile))
                .collect(Collectors.toList());
    }

    @Override
    public List<ClassScheduleDTO> getStudentSchedule(UUID studentId, UUID semesterId, UUID currentUserId, String currentUserRole) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role != UserRole.ADMIN && !currentUserId.equals(studentId)) {
            throw new CustomException("Bạn chỉ có quyền xem thời khóa biểu của chính mình", HttpStatus.FORBIDDEN);
        }

        List<ClassEnrollment> enrollments = enrollmentRepository.findByStudentIdAndStatus(studentId, EnrollmentStatus.ACTIVE);
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> enrolledClassIds = enrollments.stream()
                .map(ClassEnrollment::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (enrolledClassIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Class> classes = classRepository.findAllById(enrolledClassIds);
        if (semesterId != null) {
            classes = classes.stream()
                    .filter(c -> semesterId.equals(c.getSemesterId()))
                    .collect(Collectors.toList());
        }

        if (classes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, Class> classMap = classes.stream()
                .collect(Collectors.toMap(Class::getClassId, c -> c));

        List<UUID> activeClassIds = classes.stream().map(Class::getClassId).collect(Collectors.toList());
        List<ClassSchedule> schedules = scheduleRepository.findByClassIdInOrderByDayOfWeekAscStartTimeAsc(activeClassIds);
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch load Subjects, Instructors, Profiles
        Set<UUID> subjectIds = classes.stream()
                .map(Class::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Subject> subjectMap = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getSubjectId, s -> s));

        Set<UUID> instructorIds = classes.stream()
                .map(Class::getInstructorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> instructorMap = userRepository.findAllById(instructorIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));
        Map<UUID, UserProfile> profileMap = userProfileRepository.findAllById(instructorIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        return schedules.stream()
                .map(s -> {
                    Class cl = classMap.get(s.getClassId());
                    Subject subj = cl != null && cl.getSubjectId() != null ? subjectMap.get(cl.getSubjectId()) : null;
                    User inst = cl != null && cl.getInstructorId() != null ? instructorMap.get(cl.getInstructorId()) : null;
                    UserProfile prof = cl != null && cl.getInstructorId() != null ? profileMap.get(cl.getInstructorId()) : null;
                    return scheduleConverter.toDTO(s, cl, subj, inst, prof);
                })
                .sorted(Comparator.comparing(ClassScheduleDTO::getDayOfWeek)
                        .thenComparing(ClassScheduleDTO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ClassScheduleDTO::getStartPeriod, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClassScheduleDTO createSchedule(UUID classId, CreateClassScheduleDTO dto, UUID currentUserId, String currentUserRole) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        validateInstructorOrAdmin(clazz, currentUserId, currentUserRole, "Bạn không có quyền thêm lịch học cho lớp này");

        if (dto.getStartPeriod() != null && dto.getEndPeriod() != null && dto.getStartPeriod() > dto.getEndPeriod()) {
            throw new CustomException("Tiết bắt đầu không thể lớn hơn tiết kết thúc", HttpStatus.BAD_REQUEST);
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null && dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new CustomException("Giờ bắt đầu không thể sau giờ kết thúc", HttpStatus.BAD_REQUEST);
        }

        ClassSchedule entity = scheduleConverter.toEntity(dto, classId);
        ClassSchedule saved = scheduleRepository.save(entity);

        Subject subject = clazz.getSubjectId() != null ? subjectRepository.findById(clazz.getSubjectId()).orElse(null) : null;
        User instructor = clazz.getInstructorId() != null ? userRepository.findById(clazz.getInstructorId()).orElse(null) : null;
        UserProfile instructorProfile = clazz.getInstructorId() != null ? userProfileRepository.findById(clazz.getInstructorId()).orElse(null) : null;

        return scheduleConverter.toDTO(saved, clazz, subject, instructor, instructorProfile);
    }

    @Override
    @Transactional
    public ClassScheduleDTO updateSchedule(UUID scheduleId, UpdateClassScheduleDTO dto, UUID currentUserId, String currentUserRole) {
        ClassSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lịch học", HttpStatus.NOT_FOUND));

        Class clazz = classRepository.findById(schedule.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học tương ứng", HttpStatus.NOT_FOUND));

        validateInstructorOrAdmin(clazz, currentUserId, currentUserRole, "Bạn không có quyền chỉnh sửa lịch học này");

        Integer newStartPeriod = dto.getStartPeriod() != null ? dto.getStartPeriod() : schedule.getStartPeriod();
        Integer newEndPeriod = dto.getEndPeriod() != null ? dto.getEndPeriod() : schedule.getEndPeriod();
        if (newStartPeriod != null && newEndPeriod != null && newStartPeriod > newEndPeriod) {
            throw new CustomException("Tiết bắt đầu không thể lớn hơn tiết kết thúc", HttpStatus.BAD_REQUEST);
        }

        java.time.LocalTime newStartTime = dto.getStartTime() != null ? dto.getStartTime() : schedule.getStartTime();
        java.time.LocalTime newEndTime = dto.getEndTime() != null ? dto.getEndTime() : schedule.getEndTime();
        if (newStartTime != null && newEndTime != null && newStartTime.isAfter(newEndTime)) {
            throw new CustomException("Giờ bắt đầu không thể sau giờ kết thúc", HttpStatus.BAD_REQUEST);
        }

        if (dto.getDayOfWeek() != null) schedule.setDayOfWeek(dto.getDayOfWeek());
        if (dto.getStartPeriod() != null) schedule.setStartPeriod(dto.getStartPeriod());
        if (dto.getEndPeriod() != null) schedule.setEndPeriod(dto.getEndPeriod());
        if (dto.getStartTime() != null) schedule.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) schedule.setEndTime(dto.getEndTime());
        if (dto.getRoom() != null) schedule.setRoom(dto.getRoom());
        if (dto.getBuilding() != null) schedule.setBuilding(dto.getBuilding());
        if (dto.getLessonType() != null) schedule.setLessonType(dto.getLessonType());
        if (dto.getNotes() != null) schedule.setNotes(dto.getNotes());

        ClassSchedule updated = scheduleRepository.save(schedule);

        Subject subject = clazz.getSubjectId() != null ? subjectRepository.findById(clazz.getSubjectId()).orElse(null) : null;
        User instructor = clazz.getInstructorId() != null ? userRepository.findById(clazz.getInstructorId()).orElse(null) : null;
        UserProfile instructorProfile = clazz.getInstructorId() != null ? userProfileRepository.findById(clazz.getInstructorId()).orElse(null) : null;

        return scheduleConverter.toDTO(updated, clazz, subject, instructor, instructorProfile);
    }

    @Override
    @Transactional
    public void deleteSchedule(UUID scheduleId, UUID currentUserId, String currentUserRole) {
        ClassSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException("Không tìm thấy lịch học", HttpStatus.NOT_FOUND));

        Class clazz = classRepository.findById(schedule.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học tương ứng", HttpStatus.NOT_FOUND));

        validateInstructorOrAdmin(clazz, currentUserId, currentUserRole, "Bạn không có quyền xóa lịch học này");

        scheduleRepository.delete(schedule);
    }

    private void validateInstructorOrAdmin(Class clazz, UUID currentUserId, String currentUserRole, String errorMessage) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
        boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
        if (!isOwner && !isAssigned) {
            throw new CustomException(errorMessage, HttpStatus.FORBIDDEN);
        }
    }
}
