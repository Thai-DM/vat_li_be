package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {
    Optional<ExamAttempt> findByExamIdAndStudentId(UUID examId, UUID studentId);

    Optional<ExamAttempt> findTopByExamIdAndStudentIdOrderByStartedAtDesc(UUID examId, UUID studentId);

    Optional<ExamAttempt> findFirstByExamIdAndStudentIdAndStatus(UUID examId, UUID studentId, com.vatly1.example.entity.enums.AttemptStatus status);

    long countByExamIdAndStudentId(UUID examId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ExamAttempt a WHERE a.examId = :examId AND a.studentId = :studentId")
    Optional<ExamAttempt> findWithLockByExamIdAndStudentId(@Param("examId") UUID examId, @Param("studentId") UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ExamAttempt a WHERE a.attemptId = :attemptId")
    Optional<ExamAttempt> findByIdWithLock(@Param("attemptId") UUID attemptId);

    List<ExamAttempt> findByStudentId(UUID studentId);

    List<ExamAttempt> findByExamId(UUID examId);

    List<ExamAttempt> findByExamIdAndStudentIdOrderByAttemptNumberAsc(UUID examId, UUID studentId);

    boolean existsByExamIdAndStudentId(UUID examId, UUID studentId);
}
