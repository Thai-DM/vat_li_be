package com.vatly1.example.repository;

import com.vatly1.example.entity.MaterialEffectivenessStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialEffectivenessStatRepository extends JpaRepository<MaterialEffectivenessStat, MaterialEffectivenessStat.MaterialEffectivenessStatId> {

    List<MaterialEffectivenessStat> findByMaterialId(UUID materialId);

    List<MaterialEffectivenessStat> findByPeriod(String period);

    @Query("SELECT mes FROM MaterialEffectivenessStat mes, LearningMaterial lm, Topic t " +
            "WHERE mes.materialId = lm.materialId AND lm.topicId = t.topicId " +
            "AND (:subjectId IS NULL OR t.subjectId = :subjectId) " +
            "AND (:topicId IS NULL OR t.topicId = :topicId) " +
            "AND (:period IS NULL OR mes.period = :period) " +
            "ORDER BY mes.viewCount DESC")
    List<MaterialEffectivenessStat> filterStats(@Param("subjectId") UUID subjectId,
                                               @Param("topicId") UUID topicId,
                                               @Param("period") String period);

    @Modifying
    @Transactional
    void deleteByPeriod(String period);
}