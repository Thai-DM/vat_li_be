package com.vatly1.example.repository;

import com.vatly1.example.entity.TopicDifficultyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface TopicDifficultyStatRepository extends JpaRepository<TopicDifficultyStat, UUID> {

    List<TopicDifficultyStat> findByClassId(UUID classId);

    List<TopicDifficultyStat> findByClassIdAndPeriod(UUID classId, String period);

    List<TopicDifficultyStat> findBySubjectId(UUID subjectId);

    List<TopicDifficultyStat> findBySubjectIdAndPeriod(UUID subjectId, String period);

    @Query("SELECT tds FROM TopicDifficultyStat tds WHERE " +
            "(:classId IS NULL OR tds.classId = :classId) AND " +
            "(:subjectId IS NULL OR tds.subjectId = :subjectId) AND " +
            "(:period IS NULL OR tds.period = :period) " +
            "ORDER BY tds.errorRate DESC")
    List<TopicDifficultyStat> filterStats(@Param("classId") UUID classId,
                                         @Param("subjectId") UUID subjectId,
                                         @Param("period") String period);

    @Modifying
    @Transactional
    void deleteByClassIdAndPeriod(UUID classId, String period);

    @Modifying
    @Transactional
    void deleteBySubjectIdAndPeriod(UUID subjectId, String period);

    @Modifying
    @Transactional
    void deleteByPeriod(String period);
}