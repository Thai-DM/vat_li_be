package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamMatrixDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamMatrixDetailRepository extends JpaRepository<ExamMatrixDetail, UUID> {
    List<ExamMatrixDetail> findByMatrixId(UUID matrixId);
}
