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
@Table(name = "experiments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experiment {

    @Column(name = "experiment_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID experimentId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "scene_asset_url")
    private String sceneAssetUrl;

    @Column(name = "scene_assets_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode sceneAssetsJson;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "order_index")
    private Integer orderIndex;


}
