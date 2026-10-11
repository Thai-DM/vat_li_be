package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.CloDTO;
import com.vatly1.example.model.request.CreateCloDTO;
import com.vatly1.example.model.request.UpdateCloDTO;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.ICloService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CloServiceImpl implements ICloService {

    private final ICloRepository cloRepository;
    private final ISubjectRepository subjectRepository;
    private final IQuestionCloRepository questionCloRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional
    public CloDTO createClo(UUID subjectId, CreateCloDTO dto, UUID currentUserId, String userRole) {
        checkAdminPermission(currentUserId, userRole);

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new CustomException("Môn học không tồn tại", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(subject.getIsActive())) {
            throw new CustomException("Không thể tạo CLO cho môn học đã ngừng hoạt động", HttpStatus.BAD_REQUEST);
        }

        String cleanCode = dto.getCloCode().trim();
        if (cloRepository.existsBySubjectIdAndCloCode(subjectId, cleanCode)) {
            throw new CustomException("Mã CLO '" + cleanCode + "' đã tồn tại trong môn học này", HttpStatus.CONFLICT);
        }

        Clo clo = Clo.builder()
                .subjectId(subjectId)
                .cloCode(cleanCode)
                .description(dto.getDescription().trim())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        Clo saved = cloRepository.save(clo);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public CloDTO updateClo(UUID cloId, UpdateCloDTO dto, UUID currentUserId, String userRole) {
        checkAdminPermission(currentUserId, userRole);

        Clo clo = cloRepository.findById(cloId)
                .orElseThrow(() -> new CustomException("Chuẩn đầu ra CLO không tồn tại", HttpStatus.NOT_FOUND));

        String cleanCode = dto.getCloCode().trim();
        if (cloRepository.existsBySubjectIdAndCloCodeAndCloIdNot(clo.getSubjectId(), cleanCode, cloId)) {
            throw new CustomException("Mã CLO '" + cleanCode + "' đã tồn tại trong môn học này", HttpStatus.CONFLICT);
        }

        clo.setCloCode(cleanCode);
        clo.setDescription(dto.getDescription().trim());
        if (dto.getOrderIndex() != null) {
            clo.setOrderIndex(dto.getOrderIndex());
        }
        if (dto.getIsActive() != null) {
            clo.setIsActive(dto.getIsActive());
        }

        Clo updated = cloRepository.save(clo);
        return mapToDTO(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CloDTO> getClosBySubject(UUID subjectId, Boolean activeOnly) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Môn học không tồn tại", HttpStatus.NOT_FOUND);
        }

        List<Clo> clos;
        if (Boolean.TRUE.equals(activeOnly)) {
            clos = cloRepository.findBySubjectIdAndIsActiveTrueOrderByOrderIndexAsc(subjectId);
        } else {
            clos = cloRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId);
        }

        return clos.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CloDTO getCloById(UUID cloId) {
        Clo clo = cloRepository.findById(cloId)
                .orElseThrow(() -> new CustomException("Chuẩn đầu ra CLO không tồn tại", HttpStatus.NOT_FOUND));
        return mapToDTO(clo);
    }

    @Override
    @Transactional
    public CloDTO toggleCloActive(UUID cloId, boolean isActive, UUID currentUserId, String userRole) {
        checkAdminPermission(currentUserId, userRole);

        Clo clo = cloRepository.findById(cloId)
                .orElseThrow(() -> new CustomException("Chuẩn đầu ra CLO không tồn tại", HttpStatus.NOT_FOUND));

        clo.setIsActive(isActive);
        Clo updated = cloRepository.save(clo);
        return mapToDTO(updated);
    }

    @Override
    @Transactional
    public void deleteClo(UUID cloId, UUID currentUserId, String userRole) {
        checkAdminPermission(currentUserId, userRole);

        Clo clo = cloRepository.findById(cloId)
                .orElseThrow(() -> new CustomException("Chuẩn đầu ra CLO không tồn tại", HttpStatus.NOT_FOUND));

        long questionCount = questionCloRepository.countByCloId(cloId);
        if (questionCount > 0) {
            throw new CustomException("Không thể xóa CLO đang được liên kết với " + questionCount + " câu hỏi. Vui lòng chuyển trạng thái sang ngưng hoạt động (inactive)", HttpStatus.CONFLICT);
        }

        cloRepository.delete(clo);
    }

    private void checkAdminPermission(UUID currentUserId, String userRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
            if (!isAdmin) {
                throw new CustomException("Chỉ quản trị viên (ADMIN) mới có quyền cấu hình chuẩn đầu ra CLO", HttpStatus.FORBIDDEN);
            }
            return;
        }

        if ("ADMIN".equalsIgnoreCase(userRole) || "ROLE_ADMIN".equalsIgnoreCase(userRole)) {
            return;
        }

        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId).orElse(null);
            if (user != null && user.getRole() == UserRole.ADMIN) {
                return;
            }
        }

        throw new CustomException("Chỉ quản trị viên (ADMIN) mới có quyền cấu hình chuẩn đầu ra CLO", HttpStatus.FORBIDDEN);
    }

    private CloDTO mapToDTO(Clo clo) {
        long questionCount = questionCloRepository.countByCloId(clo.getCloId());
        return CloDTO.builder()
                .cloId(clo.getCloId())
                .subjectId(clo.getSubjectId())
                .cloCode(clo.getCloCode())
                .description(clo.getDescription())
                .orderIndex(clo.getOrderIndex())
                .isActive(clo.getIsActive())
                .questionCount(questionCount)
                .createdAt(clo.getCreatedAt())
                .updatedAt(clo.getUpdatedAt())
                .build();
    }
}
