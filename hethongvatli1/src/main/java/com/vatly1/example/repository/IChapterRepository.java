package com.vatly1.example.repository;

import com.vatly1.example.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findBySubjectIdOrderByOrderIndexAsc(UUID subjectId);
    List<Chapter> findBySubjectIdAndIsActiveTrueOrderByOrderIndexAsc(UUID subjectId);
    Optional<Chapter> findBySubjectIdAndChapterCode(UUID subjectId, String chapterCode);
    boolean existsBySubjectIdAndChapterCode(UUID subjectId, String chapterCode);
    boolean existsBySubjectIdAndChapterCodeAndChapterIdNot(UUID subjectId, String chapterCode, UUID chapterId);
    boolean existsBySubjectId(UUID subjectId);
}
