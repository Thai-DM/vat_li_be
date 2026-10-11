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
    java.util.List<ClassEnrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status);
    
    Page<ClassEnrollment> findByStudentId(UUID studentId, Pageable pageable);
    Page<ClassEnrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status, Pageable pageable);
    java.util.List<ClassEnrollment> findByStudentId(UUID studentId);
    java.util.List<ClassEnrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status);
    
    boolean existsByClassIdAndStudentId(UUID classId, UUID studentId);
    Optional<ClassEnrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);
    
    void deleteByClassIdAndStudentId(UUID classId, UUID studentId);
    
    int countByClassIdAndStatus(UUID classId, EnrollmentStatus status);

    boolean existsByStudentIdAndSubjectIdAndSemesterIdAndStatus(UUID studentId, UUID subjectId, UUID semesterId, EnrollmentStatus status);

    Optional<ClassEnrollment> findByStudentIdAndSubjectIdAndSemesterIdAndStatus(UUID studentId, UUID subjectId, UUID semesterId, EnrollmentStatus status);

    java.util.List<ClassEnrollment> findByStudentIdAndSubjectIdAndSemesterId(UUID studentId, UUID subjectId, UUID semesterId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(ce) > 0 FROM ClassEnrollment ce WHERE ce.studentId = :studentId AND ce.subjectId = :subjectId AND (ce.status IS NULL OR ce.status = com.vatly1.example.entity.enums.EnrollmentStatus.ACTIVE)")
    boolean isStudentActivelyEnrolledInSubject(@org.springframework.data.repository.query.Param("studentId") UUID studentId, @org.springframework.data.repository.query.Param("subjectId") UUID subjectId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(ce) > 0 FROM ClassEnrollment ce WHERE ce.classId = :classId AND ce.studentId = :studentId AND (ce.status IS NULL OR ce.status = com.vatly1.example.entity.enums.EnrollmentStatus.ACTIVE)")
    boolean isStudentActivelyEnrolledInClass(@org.springframework.data.repository.query.Param("classId") UUID classId, @org.springframework.data.repository.query.Param("studentId") UUID studentId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT ce.subjectId FROM ClassEnrollment ce WHERE ce.studentId = :studentId AND (ce.status IS NULL OR ce.status = com.vatly1.example.entity.enums.EnrollmentStatus.ACTIVE)")
    java.util.List<UUID> findActiveSubjectIdsByStudentId(@org.springframework.data.repository.query.Param("studentId") UUID studentId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT ce.classId FROM ClassEnrollment ce WHERE ce.studentId = :studentId AND (ce.status IS NULL OR ce.status = com.vatly1.example.entity.enums.EnrollmentStatus.ACTIVE)")
    java.util.List<UUID> findActiveClassIdsByStudentId(@org.springframework.data.repository.query.Param("studentId") UUID studentId);
}