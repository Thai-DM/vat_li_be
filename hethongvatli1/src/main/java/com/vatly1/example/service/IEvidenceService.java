package com.vatly1.example.service;

import com.vatly1.example.dto.dto.EvidenceDTO;
import com.vatly1.example.entity.enums.EvidenceSourceType;

import java.util.List;
import java.util.UUID;

public interface IEvidenceService {
    List<EvidenceDTO> getMyEvidence(UUID studentId);
    List<EvidenceDTO> getStudentEvidence(UUID targetStudentId, UUID requesterId, String role);
    List<EvidenceDTO> getClassEvidence(UUID classId, UUID requesterId, String role);
    void recordEvidence(UUID studentId, EvidenceSourceType type, UUID sourceId, UUID fileId, String fileUrl);
}