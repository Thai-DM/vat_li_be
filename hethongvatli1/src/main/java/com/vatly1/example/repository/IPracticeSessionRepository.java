package com.vatly1.example.repository;

import com.vatly1.example.entity.PracticeSession;
import com.vatly1.example.entity.enums.PracticeSessionStatus;
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
public interface IPracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {
    List<PracticeSession> findByStudentIdAndClassIdOrderByStartedAtDesc(UUID studentId, UUID classId);
    List<PracticeSession> findByStudentIdAndSubjectIdAndStatusOrderByStartedAtDesc(UUID studentId, UUID subjectId, PracticeSessionStatus status);
    List<PracticeSession> findByClassIdAndStatus(UUID classId, PracticeSessionStatus status);
    long countByStudentIdAndClassId(UUID studentId, UUID classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PracticeSession p WHERE p.sessionId = :sessionId")
    Optional<PracticeSession> findByIdWithLock(@Param("sessionId") UUID sessionId);
}
