package com.vatly1.example.repository;

import com.vatly1.example.entity.AiTopicGapStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiTopicGapStatRepository extends JpaRepository<AiTopicGapStat, UUID> {

    List<AiTopicGapStat> findBySubjectId(UUID subjectId);

    List<AiTopicGapStat> findBySubjectIdAndPeriod(UUID subjectId, String period);

    @Query("SELECT ags FROM AiTopicGapStat ags WHERE " +
            "(:subjectId IS NULL OR ags.subjectId = :subjectId) AND " +
            "(:period IS NULL OR ags.period = :period) " +
            "ORDER BY ags.refusalCount DESC")
    List<AiTopicGapStat> filterStats(@Param("subjectId") UUID subjectId,
                                     @Param("period") String period);

    @Modifying
    @Transactional
    void deleteBySubjectIdAndPeriod(UUID subjectId, String period);

    @Modifying
    @Transactional
    void deleteByPeriod(String period);
}