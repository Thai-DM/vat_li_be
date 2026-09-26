package com.vatly1.example.repository;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.ClassStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IClassRepository extends JpaRepository<Class, UUID> {
    Page<Class> findBySubjectIdAndSemesterIdAndStatus(UUID subjectId, UUID semesterId, ClassStatus status, Pageable pageable);
    Page<Class> findBySubjectIdAndSemesterId(UUID subjectId, UUID semesterId, Pageable pageable);
    Page<Class> findBySubjectIdAndStatus(UUID subjectId, ClassStatus status, Pageable pageable);
    Page<Class> findBySemesterIdAndStatus(UUID semesterId, ClassStatus status, Pageable pageable);
    Page<Class> findBySubjectId(UUID subjectId, Pageable pageable);
    Page<Class> findBySemesterId(UUID semesterId, Pageable pageable);
    Page<Class> findByStatus(ClassStatus status, Pageable pageable);

    Page<Class> findByInstructorId(UUID instructorId, Pageable pageable);

    @Query("SELECT c FROM Class c WHERE c.instructorId = :userId OR c.classId IN (SELECT cs.classId FROM ClassStaff cs WHERE cs.userId = :userId)")
    Page<Class> findByInstructorIdOrStaffUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM Class c WHERE c.classId IN (SELECT cs.classId FROM ClassStaff cs WHERE cs.userId = :userId)")
    Page<Class> findByStaffUserId(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT c FROM Class c WHERE c.classId IN (SELECT ce.classId FROM ClassEnrollment ce WHERE ce.studentId = :studentId)")
    Page<Class> findByStudentId(@Param("studentId") UUID studentId, Pageable pageable);

    java.util.Optional<Class> findByClassCode(String classCode);
    boolean existsByClassCode(String classCode);
}