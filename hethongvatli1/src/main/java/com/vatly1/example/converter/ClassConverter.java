package com.vatly1.example.converter;

import com.vatly1.example.dto.ClassDTO;
import com.vatly1.example.dto.CreateClassDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ClassStatus;
import org.springframework.stereotype.Component;

@Component
public class ClassConverter {

    public ClassDTO toClassDTO(Class classObj) {
        if (classObj == null) {
            return null;
        }
        return ClassDTO.builder()
                .classId(classObj.getClassId())
                .subjectId(classObj.getSubjectId())
                .semesterId(classObj.getSemesterId())
                .classCode(classObj.getClassCode())
                .instructorId(classObj.getInstructorId())
                .maxStudents(classObj.getMaxStudents())
                .status(classObj.getStatus())
                .createdAt(classObj.getCreatedAt())
                .build();
    }

    public Class toClass(CreateClassDTO dto) {
        if (dto == null) {
            return null;
        }
        return Class.builder()
                .subjectId(dto.getSubjectId())
                .semesterId(dto.getSemesterId())
                .classCode(dto.getClassCode())
                .maxStudents(dto.getMaxStudents() != null ? dto.getMaxStudents() : 50)
                .status(ClassStatus.DRAFT)
                .build();
    }
}
