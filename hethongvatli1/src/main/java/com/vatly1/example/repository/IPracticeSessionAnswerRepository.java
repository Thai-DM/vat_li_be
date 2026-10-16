package com.vatly1.example.repository;

import com.vatly1.example.entity.PracticeSessionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vatly1.example.repository.projection.StudentLatestAnswerProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IPracticeSessionAnswerRepository extends JpaRepository<PracticeSessionAnswer, UUID> {
    List<PracticeSessionAnswer> findBySessionId(UUID sessionId);
    Optional<PracticeSessionAnswer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
    List<PracticeSessionAnswer> findBySessionIdIn(List<UUID> sessionIds);
    void deleteBySessionId(UUID sessionId);

    @Query(value = """
        WITH exam_answers_ranked AS (
            SELECT 
                CAST(att.student_id AS VARCHAR) AS student_id,
                CAST(ans.question_id AS VARCHAR) AS question_id,
                CAST(qb.topic_id AS VARCHAR) AS topic_id,
                ans.is_correct,
                att.started_at AS answered_time,
                1 AS source_priority
            FROM exam_attempts att
            JOIN exams e ON e.exam_id = att.exam_id
            JOIN classes c ON c.class_id = e.class_id
            JOIN exam_answers ans ON ans.attempt_id = att.attempt_id
            JOIN question_bank qb ON qb.question_id = ans.question_id
            WHERE att.student_id IN (:studentIds)
              AND att.status IN ('GRADED', 'SUBMITTED')
              AND e.exam_type != 'FINAL'
              AND c.subject_id = :subjectId
              AND qb.subject_id = :subjectId
              AND qb.topic_id IS NOT NULL
              AND ans.is_correct IS NOT NULL
        ),
        practice_answers_ranked AS (
            SELECT 
                CAST(ps.student_id AS VARCHAR) AS student_id,
                CAST(psa.question_id AS VARCHAR) AS question_id,
                CAST(qb.topic_id AS VARCHAR) AS topic_id,
                psa.is_correct,
                ps.started_at AS answered_time,
                2 AS source_priority
            FROM practice_sessions ps
            JOIN practice_session_answers psa ON psa.session_id = ps.session_id
            JOIN question_bank qb ON qb.question_id = psa.question_id
            WHERE ps.student_id IN (:studentIds)
              AND ps.status = 'COMPLETED'
              AND ps.subject_id = :subjectId
              AND qb.subject_id = :subjectId
              AND qb.topic_id IS NOT NULL
              AND psa.is_correct IS NOT NULL
        ),
        combined_answers AS (
            SELECT * FROM exam_answers_ranked
            UNION ALL
            SELECT * FROM practice_answers_ranked
        ),
        latest_answers AS (
            SELECT 
                student_id,
                topic_id,
                question_id,
                is_correct,
                ROW_NUMBER() OVER (
                    PARTITION BY student_id, question_id 
                    ORDER BY source_priority DESC, answered_time DESC NULLS LAST
                ) AS rn
            FROM combined_answers
        )
        SELECT 
            student_id AS studentId,
            topic_id AS topicId,
            question_id AS questionId,
            is_correct AS isCorrect
        FROM latest_answers
        WHERE rn = 1
        """, nativeQuery = true)
    List<StudentLatestAnswerProjection> findLatestAnswersByStudentIdsAndSubjectId(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("subjectId") UUID subjectId);
}

