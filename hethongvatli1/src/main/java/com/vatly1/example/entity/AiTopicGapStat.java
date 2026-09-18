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

@Entity
@Table(name = "ai_topic_gap_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTopicGapStat {

    @Column(name = "gap_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID gapId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "topic_id")
    private java.util.UUID topicId;

    @Column(name = "refusal_count")
    private Integer refusalCount;

    @Column(name = "frequent_query_sample")
    private String frequentQuerySample;

    @Column(name = "period")
    private String period;

    @Column(name = "generated_at")
    private java.time.Instant generatedAt;


}
