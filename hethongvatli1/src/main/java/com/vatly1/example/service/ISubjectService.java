package com.vatly1.example.service;

import com.vatly1.example.dto.request.CreateSubjectDTO;
import com.vatly1.example.dto.dto.SubjectDTO;
import com.vatly1.example.dto.request.UpdateSubjectDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ISubjectService {
    Page<SubjectDTO> getSubjects(Boolean isActive, int page, int size);
    SubjectDTO getSubjectById(UUID id);
    SubjectDTO createSubject(CreateSubjectDTO dto);
    SubjectDTO updateSubject(UUID id, UpdateSubjectDTO dto);
    SubjectDTO toggleSubjectStatus(UUID id);
}