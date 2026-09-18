package com.vatly1.example.entity;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "object_type")
    private String objectType;

    @Column(name = "object_id")
    private UUID objectId;

    @Column(name = "metadata_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadataJson;

    @Column(name = "created_at")
    private Instant createdAt;
}