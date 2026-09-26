package com.vatly1.example.converter;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassSchedule;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.LessonType;
import com.vatly1.example.model.dto.ClassScheduleDTO;
import com.vatly1.example.model.request.CreateClassScheduleDTO;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClassScheduleConverter {

    public static String getDayOfWeekText(Integer dayOfWeek) {
        if (dayOfWeek == null) {
            return null;
        }
        return switch (dayOfWeek) {
            case 2 -> "Thứ Hai";
            case 3 -> "Thứ Ba";
            case 4 -> "Thứ Tư";
            case 5 -> "Thứ Năm";
            case 6 -> "Thứ Sáu";
            case 7 -> "Thứ Bảy";
            case 8 -> "Chủ Nhật";
            default -> "Thứ " + dayOfWeek;
        };
    }

    public ClassScheduleDTO toDTO(ClassSchedule schedule, Class clazz, Subject subject, User instructor, UserProfile instructorProfile) {
        if (schedule == null) {
            return null;
        }

        String instructorName = null;
        if (instructorProfile != null && instructorProfile.getFullName() != null && !instructorProfile.getFullName().isBlank()) {
            instructorName = instructorProfile.getFullName();
        } else if (instructor != null) {
            instructorName = instructor.getUsername();
        }

        return ClassScheduleDTO.builder()
                .scheduleId(schedule.getScheduleId())
                .classId(schedule.getClassId())
                .classCode(clazz != null ? clazz.getClassCode() : null)
                .subjectId(clazz != null ? clazz.getSubjectId() : null)
                .subjectCode(subject != null ? subject.getSubjectCode() : null)
                .subjectName(subject != null ? subject.getSubjectName() : null)
                .semesterId(clazz != null ? clazz.getSemesterId() : null)
                .instructorId(clazz != null ? clazz.getInstructorId() : null)
                .instructorName(instructorName)
                .dayOfWeek(schedule.getDayOfWeek())
                .dayOfWeekText(getDayOfWeekText(schedule.getDayOfWeek()))
                .startPeriod(schedule.getStartPeriod())
                .endPeriod(schedule.getEndPeriod())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .room(schedule.getRoom())
                .building(schedule.getBuilding())
                .lessonType(schedule.getLessonType() != null ? schedule.getLessonType() : LessonType.THEORY)
                .notes(schedule.getNotes())
                .createdAt(schedule.getCreatedAt())
                .build();
    }

    public ClassSchedule toEntity(CreateClassScheduleDTO dto, UUID classId) {
        if (dto == null) {
            return null;
        }
        return ClassSchedule.builder()
                .classId(classId)
                .dayOfWeek(dto.getDayOfWeek())
                .startPeriod(dto.getStartPeriod())
                .endPeriod(dto.getEndPeriod())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .room(dto.getRoom())
                .building(dto.getBuilding())
                .lessonType(dto.getLessonType() != null ? dto.getLessonType() : LessonType.THEORY)
                .notes(dto.getNotes())
                .build();
    }
}
