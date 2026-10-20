package com.vatly1.example.repository;

import com.vatly1.example.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamRepository extends JpaRepository<Exam, UUID> {
    List<Exam> findByClassId(UUID classId);
    List<Exam> findByClassIdAndIsPublishedTrue(UUID classId);
    List<Exam> findByClassIdIn(List<UUID> classIds);
    List<Exam> findByClassIdInAndIsPublishedTrue(List<UUID> classIds);
    boolean existsByExamIdAndCreatedBy(UUID examId, UUID createdBy);
    boolean existsByMatrixId(UUID matrixId);
    long countByMatrixId(UUID matrixId);
    List<Exam> findByMatrixId(UUID matrixId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Exam e WHERE e.examId = :examId")
    java.util.Optional<Exam> findByIdForUpdate(@org.springframework.data.repository.query.Param("examId") UUID examId);

    boolean existsByClassIdAndMatrixId(UUID classId, UUID matrixId);
    List<Exam> findByClassIdAndMatrixId(UUID classId, UUID matrixId);
    boolean existsByClassIdAndExamType(UUID classId, com.vatly1.example.entity.enums.ExamType examType);
    List<Exam> findByClassIdAndExamType(UUID classId, com.vatly1.example.entity.enums.ExamType examType);
}