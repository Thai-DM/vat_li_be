package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamViolationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamViolationLogRepository extends JpaRepository<ExamViolationLog, UUID> {

    List<ExamViolationLog> findByAttemptIdOrderByCreatedAtAsc(UUID attemptId);

    List<ExamViolationLog> findByAttemptIdOrderByCreatedAtDesc(UUID attemptId);

    List<ExamViolationLog> findByExamIdOrderByCreatedAtDesc(UUID examId);

    List<ExamViolationLog> findByExamIdAndStudentIdOrderByCreatedAtAsc(UUID examId, UUID studentId);

    long countByAttemptId(UUID attemptId);

    long countByExamId(UUID examId);

    @Modifying
    @Query("DELETE FROM ExamViolationLog v WHERE v.examId = :examId")
    void deleteByExamId(@Param("examId") UUID examId);

    @Modifying
    @Query("DELETE FROM ExamViolationLog v WHERE v.attemptId = :attemptId")
    void deleteByAttemptId(@Param("attemptId") UUID attemptId);
}
