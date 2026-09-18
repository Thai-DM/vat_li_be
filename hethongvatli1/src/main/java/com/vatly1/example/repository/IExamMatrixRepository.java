package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamMatrixRepository extends JpaRepository<ExamMatrix, UUID> {
    List<ExamMatrix> findBySubjectId(UUID subjectId);
}
