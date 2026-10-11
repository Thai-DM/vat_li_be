package com.vatly1.example.service;

import com.vatly1.example.model.dto.CloDTO;
import com.vatly1.example.model.request.CreateCloDTO;
import com.vatly1.example.model.request.UpdateCloDTO;

import java.util.List;
import java.util.UUID;

public interface ICloService {

    CloDTO createClo(UUID subjectId, CreateCloDTO dto, UUID currentUserId, String userRole);

    CloDTO updateClo(UUID cloId, UpdateCloDTO dto, UUID currentUserId, String userRole);

    List<CloDTO> getClosBySubject(UUID subjectId, Boolean activeOnly);

    CloDTO getCloById(UUID cloId);

    CloDTO toggleCloActive(UUID cloId, boolean isActive, UUID currentUserId, String userRole);

    void deleteClo(UUID cloId, UUID currentUserId, String userRole);
}
