package com.vatly1.example.repository;

import com.vatly1.example.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamRepository extends JpaRepository<Exam, UUID> {
    List<Exam> findByClassId(UUID classId);
    boolean existsByExamIdAndCreatedBy(UUID examId, UUID createdBy);
}
