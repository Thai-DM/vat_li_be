package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "\"key\"", length = 100, nullable = false)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT", nullable = false)
    private String settingValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.updatedAt = Instant.now();
    }
}
