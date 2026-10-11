package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.entity.enums.MatrixStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IExamMatrixRepository extends JpaRepository<ExamMatrix, UUID> {
    List<ExamMatrix> findBySubjectId(UUID subjectId);
    List<ExamMatrix> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId);
    List<ExamMatrix> findAllByOrderByCreatedAtDesc();

    List<ExamMatrix> findBySubjectIdAndExamType(UUID subjectId, ExamType examType);
    List<ExamMatrix> findBySubjectIdAndExamTypeAndStatus(UUID subjectId, ExamType examType, MatrixStatus status);
    List<ExamMatrix> findBySubjectIdAndStatus(UUID subjectId, MatrixStatus status);

    List<ExamMatrix> findByMatrixFamilyIdOrderByRevisionNumberAsc(UUID matrixFamilyId);
    Optional<ExamMatrix> findByMatrixFamilyIdAndRevisionNumber(UUID matrixFamilyId, Integer revisionNumber);
    boolean existsByMatrixFamilyIdAndRevisionNumber(UUID matrixFamilyId, Integer revisionNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT m FROM ExamMatrix m WHERE m.matrixId = :matrixId")
    Optional<ExamMatrix> findByIdForUpdate(@org.springframework.data.repository.query.Param("matrixId") UUID matrixId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT m FROM ExamMatrix m WHERE m.matrixFamilyId = :matrixFamilyId")
    List<ExamMatrix> findByMatrixFamilyIdForUpdate(@org.springframework.data.repository.query.Param("matrixFamilyId") UUID matrixFamilyId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(m.revisionNumber), 0) FROM ExamMatrix m WHERE m.matrixFamilyId = :matrixFamilyId")
    Integer findMaxRevisionNumberByMatrixFamilyId(@org.springframework.data.repository.query.Param("matrixFamilyId") UUID matrixFamilyId);
}