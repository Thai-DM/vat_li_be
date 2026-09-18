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
@Table(name = "ai_message_citations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessageCitation {

    @Column(name = "citation_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID citationId;

    @Column(name = "message_id")
    private java.util.UUID messageId;

    @Column(name = "material_id")
    private java.util.UUID materialId;

    @Column(name = "version_id")
    private java.util.UUID versionId;

    @Column(name = "excerpt")
    private String excerpt;

    @Column(name = "relevance_score")
    private java.math.BigDecimal relevanceScore;


}
