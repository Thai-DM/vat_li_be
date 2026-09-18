package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "topic_difficulty_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicDifficultyStat {

    @Column(name = "stat_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID statId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "class_id")
    private java.util.UUID classId;

    @Column(name = "avg_score")
    private java.math.BigDecimal avgScore;

    @Column(name = "error_rate")
    private java.math.BigDecimal errorRate;

    @Column(name = "common_wrong_options_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode commonWrongOptionsJson;

    @Column(name = "period")
    private String period;

    @Column(name = "generated_at")
    private java.time.Instant generatedAt;


}