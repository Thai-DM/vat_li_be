package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IExamParticipantRepository extends JpaRepository<ExamParticipant, UUID> {

    List<ExamParticipant> findByExamId(UUID examId);

    List<ExamParticipant> findByStudentId(UUID studentId);

    boolean existsByExamIdAndStudentId(UUID examId, UUID studentId);

    Optional<ExamParticipant> findByExamIdAndStudentId(UUID examId, UUID studentId);

    void deleteByExamIdAndStudentId(UUID examId, UUID studentId);
}
