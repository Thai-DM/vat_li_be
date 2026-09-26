package com.vatly1.example.repository;

import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IClassEnrollmentRepository extends JpaRepository<ClassEnrollment, UUID> {
    Page<ClassEnrollment> findByClassId(UUID classId, Pageable pageable);
    Page<ClassEnrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status, Pageable pageable);
    
    Page<ClassEnrollment> findByStudentId(UUID studentId, Pageable pageable);
    Page<ClassEnrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status, Pageable pageable);
    java.util.List<ClassEnrollment> findByStudentId(UUID studentId);
    java.util.List<ClassEnrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status);
    
    boolean existsByClassIdAndStudentId(UUID classId, UUID studentId);
    Optional<ClassEnrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);
    
    void deleteByClassIdAndStudentId(UUID classId, UUID studentId);
    
    int countByClassIdAndStatus(UUID classId, EnrollmentStatus status);
}