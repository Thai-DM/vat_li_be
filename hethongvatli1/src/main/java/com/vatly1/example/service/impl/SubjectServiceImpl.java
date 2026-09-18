package com.vatly1.example.service.impl;

import com.vatly1.example.converter.SubjectConverter;
import com.vatly1.example.dto.request.CreateSubjectDTO;
import com.vatly1.example.dto.dto.SubjectDTO;
import com.vatly1.example.dto.request.UpdateSubjectDTO;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.service.ISubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements ISubjectService {

    private final ISubjectRepository subjectRepository;
    private final SubjectConverter subjectConverter;

    @Override
    public Page<SubjectDTO> getSubjects(Boolean isActive, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Subject> subjects;
        
        if (isActive != null) {
            subjects = subjectRepository.findByIsActive(isActive, pageable);
        } else {
            subjects = subjectRepository.findAll(pageable);
        }
        
        return subjects.map(subjectConverter::toSubjectDTO);
    }

    @Override
    public SubjectDTO getSubjectById(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy môn học", HttpStatus.NOT_FOUND));
        return subjectConverter.toSubjectDTO(subject);
    }

    @Override
    @Transactional
    public SubjectDTO createSubject(CreateSubjectDTO dto) {
        if (subjectRepository.existsBySubjectCode(dto.getSubjectCode())) {
            throw new CustomException("Mã môn học đã tồn tại", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Subject subject = subjectConverter.toSubject(dto);

        subject = subjectRepository.save(subject);
        return subjectConverter.toSubjectDTO(subject);
    }

    @Override
    @Transactional
    public SubjectDTO updateSubject(UUID id, UpdateSubjectDTO dto) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy môn học", HttpStatus.NOT_FOUND));

        subject.setSubjectName(dto.getSubjectName());
        subject.setDescription(dto.getDescription());

        subject = subjectRepository.save(subject);
        return subjectConverter.toSubjectDTO(subject);
    }

    @Override
    @Transactional
    public SubjectDTO toggleSubjectStatus(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy môn học", HttpStatus.NOT_FOUND));

        subject.setIsActive(!subject.getIsActive());

        subject = subjectRepository.save(subject);
        return subjectConverter.toSubjectDTO(subject);
    }
}