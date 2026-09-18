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
@Table(name = "dashboard_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "period")
    private String period;

    @Column(name = "data_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode dataJson;

    @Column(name = "generated_at")
    private Instant generatedAt;
}