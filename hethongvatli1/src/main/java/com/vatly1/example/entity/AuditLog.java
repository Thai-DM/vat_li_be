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
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action")
    private String action;

    @Column(name = "entity")
    private String entity;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "old_value")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode oldValue;

    @Column(name = "new_value")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode newValue;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at")
    private Instant createdAt;
}
