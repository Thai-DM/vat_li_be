package com.vatly1.example.service.impl;

import com.vatly1.example.dto.EvidenceDTO;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.EvidenceRepository;
import com.vatly1.example.entity.enums.EvidenceSourceType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.EvidenceRepositoryJpaRepo;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.service.IEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvidenceServiceImpl implements IEvidenceService {

    private final EvidenceRepositoryJpaRepo evidenceRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository enrollmentRepository;
    private final IClassStaffRepository classStaffRepository;

    @Override
    public List<EvidenceDTO> getMyEvidence(UUID studentId) {
        return evidenceRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvidenceDTO> getStudentEvidence(UUID targetStudentId, UUID requesterId, String role) {
        if ("STUDENT".equals(role)) {
            if (!targetStudentId.equals(requesterId)) {
                throw new CustomException("Access denied: You cannot view another student's evidence", HttpStatus.FORBIDDEN);
            }
        } else if ("INSTRUCTOR".equals(role)) {
            List<Class> instructorClasses = classRepository.findByInstructorId(requesterId, Pageable.unpaged()).getContent();
            boolean teachesStudent = instructorClasses.stream()
                    .anyMatch(c -> enrollmentRepository.existsByClassIdAndStudentId(c.getClassId(), targetStudentId));
            if (!teachesStudent) {
                // Check class staff
                boolean isStaffOfStudentClass = enrollmentRepository.findByStudentId(targetStudentId, Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(ce -> classStaffRepository.existsByClassIdAndUserId(ce.getClassId(), requesterId));
                if (!isStaffOfStudentClass) {
                    throw new CustomException("Access denied: You do not instruct any class with this student", HttpStatus.FORBIDDEN);
                }
            }
        }

        return evidenceRepository.findByStudentIdOrderByCreatedAtDesc(targetStudentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvidenceDTO> getClassEvidence(UUID classId, UUID requesterId, String role) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException("Class not found", HttpStatus.NOT_FOUND));

        if ("INSTRUCTOR".equals(role)) {
            boolean isAssigned = clazz.getInstructorId() != null && clazz.getInstructorId().equals(requesterId);
            boolean isStaff = classStaffRepository.existsByClassIdAndUserId(classId, requesterId);
            if (!isAssigned && !isStaff) {
                throw new CustomException("Access denied: You are not teaching this class", HttpStatus.FORBIDDEN);
            }
        } else if ("STUDENT".equals(role)) {
            throw new CustomException("Students cannot view class evidence", HttpStatus.FORBIDDEN);
        }

        List<UUID> studentIds = enrollmentRepository.findByClassId(classId, Pageable.unpaged())
                .getContent().stream()
                .map(ClassEnrollment::getStudentId)
                .collect(Collectors.toList());

        if (studentIds.isEmpty()) {
            return Collections.emptyList();
        }

        return evidenceRepository.findByStudentIdInOrderByCreatedAtDesc(studentIds).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordEvidence(UUID studentId, EvidenceSourceType type, UUID sourceId, UUID fileId, String fileUrl) {
        if (evidenceRepository.existsByStudentIdAndSourceTypeAndSourceId(studentId, type, sourceId)) {
            return;
        }

        EvidenceRepository evidence = EvidenceRepository.builder()
                .studentId(studentId)
                .sourceType(type)
                .sourceId(sourceId)
                .fileId(fileId)
                .fileUrl(fileUrl)
                .createdAt(Instant.now())
                .build();

        evidenceRepository.save(evidence);
        log.info("Recorded evidence: student={}, type={}, source={}", studentId, type, sourceId);
    }

    private EvidenceDTO mapToDTO(EvidenceRepository entity) {
        return EvidenceDTO.builder()
                .evidenceId(entity.getEvidenceId())
                .studentId(entity.getStudentId())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .fileId(entity.getFileId())
                .fileUrl(entity.getFileUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
