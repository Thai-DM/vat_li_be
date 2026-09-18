package com.vatly1.example.service.impl;

import com.vatly1.example.converter.SemesterConverter;
import com.vatly1.example.dto.request.CreateSemesterDTO;
import com.vatly1.example.dto.SemesterDTO;
import com.vatly1.example.dto.request.UpdateSemesterDTO;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.service.ISemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SemesterServiceImpl implements ISemesterService {

    private final ISemesterRepository semesterRepository;
    private final SemesterConverter semesterConverter;

    @Override
    public List<SemesterDTO> getAllSemesters() {
        return semesterRepository.findAll(Sort.by("createdAt").descending())
                .stream()
                .map(semesterConverter::toSemesterDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SemesterDTO getSemesterById(UUID id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy học kỳ", HttpStatus.NOT_FOUND));
        return semesterConverter.toSemesterDTO(semester);
    }

    @Override
    @Transactional
    public SemesterDTO createSemester(CreateSemesterDTO dto) {
        if (semesterRepository.existsBySemesterNameAndAcademicYear(dto.getSemesterName(), dto.getAcademicYear())) {
            throw new CustomException("Học kỳ này trong năm học này đã tồn tại", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Semester semester = semesterConverter.toSemester(dto);

        semester = semesterRepository.save(semester);
        return semesterConverter.toSemesterDTO(semester);
    }

    @Override
    @Transactional
    public SemesterDTO updateSemester(UUID id, UpdateSemesterDTO dto) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy học kỳ", HttpStatus.NOT_FOUND));

        if (!semester.getSemesterName().equals(dto.getSemesterName()) ||
            !semester.getAcademicYear().equals(dto.getAcademicYear())) {
            if (semesterRepository.existsBySemesterNameAndAcademicYear(dto.getSemesterName(), dto.getAcademicYear())) {
                throw new CustomException("Học kỳ này trong năm học này đã tồn tại", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        semester.setSemesterName(dto.getSemesterName());
        semester.setAcademicYear(dto.getAcademicYear());
        semester.setStartDate(dto.getStartDate());
        semester.setEndDate(dto.getEndDate());

        semester = semesterRepository.save(semester);
        return semesterConverter.toSemesterDTO(semester);
    }

    @Override
    @Transactional
    public SemesterDTO setCurrentSemester(UUID id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new CustomException("Không tìm thấy học kỳ", HttpStatus.NOT_FOUND));

        // Cập nhật nguyên tử ở tầng database (row-lock trên tất cả bản ghi is_current = true hoặc semester_id = id)
        // Đảm bảo an toàn phân tán khi scale nhiều backend instances/pods
        semesterRepository.setSingleCurrentSemesterAtomic(id);

        semester.setIsCurrent(true);
        return semesterConverter.toSemesterDTO(semester);
    }
}
