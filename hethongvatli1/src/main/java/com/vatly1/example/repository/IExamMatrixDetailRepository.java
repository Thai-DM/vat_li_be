package com.vatly1.example.repository;

import com.vatly1.example.entity.ExamMatrixDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IExamMatrixDetailRepository extends JpaRepository<ExamMatrixDetail, UUID> {
    List<ExamMatrixDetail> findByMatrixId(UUID matrixId);
    void deleteByMatrixId(UUID matrixId);
    long countByMatrixId(UUID matrixId);

    List<ExamMatrixDetail> findByCloId(UUID cloId);
    List<ExamMatrixDetail> findByTopicId(UUID topicId);
    boolean existsByCloId(UUID cloId);
    boolean existsByTopicId(UUID topicId);
    long countByCloId(UUID cloId);
}