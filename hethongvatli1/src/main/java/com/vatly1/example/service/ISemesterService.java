package com.vatly1.example.service;

import com.vatly1.example.model.request.CreateSemesterDTO;
import com.vatly1.example.model.dto.SemesterDTO;
import com.vatly1.example.model.request.UpdateSemesterDTO;

import java.util.List;
import java.util.UUID;

public interface ISemesterService {
    List<SemesterDTO> getAllSemesters();
    SemesterDTO getSemesterById(UUID id);
    SemesterDTO createSemester(CreateSemesterDTO dto);
    SemesterDTO updateSemester(UUID id, UpdateSemesterDTO dto);
    SemesterDTO setCurrentSemester(UUID id);
}