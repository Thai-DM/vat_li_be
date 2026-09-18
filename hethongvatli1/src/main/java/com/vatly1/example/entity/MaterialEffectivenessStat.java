package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "material_effectiveness_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(MaterialEffectivenessStat.MaterialEffectivenessStatId.class)
public class MaterialEffectivenessStat {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MaterialEffectivenessStatId implements java.io.Serializable {
        private java.util.UUID materialId;
        private String period;
    }
    @Id
    @Column(name = "material_id")
    private java.util.UUID materialId;

    @Id
    @Column(name = "period")
    private String period;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "avg_time_spent_seconds")
    private java.math.BigDecimal avgTimeSpentSeconds;

    @Column(name = "correlated_score_improvement")
    private java.math.BigDecimal correlatedScoreImprovement;


}
