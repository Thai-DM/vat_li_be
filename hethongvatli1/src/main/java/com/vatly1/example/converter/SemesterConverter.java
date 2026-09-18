package com.vatly1.example.converter;

import com.vatly1.example.dto.request.CreateSemesterDTO;
import com.vatly1.example.dto.dto.SemesterDTO;
import com.vatly1.example.entity.Semester;
import org.springframework.stereotype.Component;

@Component
public class SemesterConverter {

    public SemesterDTO toSemesterDTO(Semester semester) {
        if (semester == null) {
            return null;
        }
        return SemesterDTO.builder()
                .semesterId(semester.getSemesterId())
                .semesterCode(semester.getSemesterCode())
                .semesterName(semester.getSemesterName())
                .academicYear(semester.getAcademicYear())
                .startDate(semester.getStartDate())
                .endDate(semester.getEndDate())
                .isCurrent(semester.getIsCurrent())
                .createdAt(semester.getCreatedAt())
                .build();
    }

    public Semester toSemester(CreateSemesterDTO dto) {
        if (dto == null) {
            return null;
        }
        return Semester.builder()
                .semesterCode(dto.getSemesterCode())
                .semesterName(dto.getSemesterName())
                .academicYear(dto.getAcademicYear())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .isCurrent(false)
                .build();
    }
}