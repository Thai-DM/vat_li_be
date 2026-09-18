package com.vatly1.example.converter;

import com.vatly1.example.dto.request.CreateSubjectDTO;
import com.vatly1.example.dto.SubjectDTO;
import com.vatly1.example.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectConverter {

    public SubjectDTO toSubjectDTO(Subject subject) {
        if (subject == null) {
            return null;
        }
        return SubjectDTO.builder()
                .subjectId(subject.getSubjectId())
                .subjectCode(subject.getSubjectCode())
                .subjectName(subject.getSubjectName())
                .description(subject.getDescription())
                .isActive(subject.getIsActive())
                .createdAt(subject.getCreatedAt())
                .build();
    }

    public Subject toSubject(CreateSubjectDTO dto) {
        if (dto == null) {
            return null;
        }
        return Subject.builder()
                .subjectCode(dto.getSubjectCode())
                .subjectName(dto.getSubjectName())
                .description(dto.getDescription())
                .isActive(true)
                .build();
    }
}
