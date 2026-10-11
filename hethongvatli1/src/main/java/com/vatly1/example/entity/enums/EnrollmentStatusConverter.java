package com.vatly1.example.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA Converter for EnrollmentStatus.
 * Bridges Java Enum (ACTIVE, DROPPED, COMPLETED) and PostgreSQL storage (VARCHAR / ENUM).
 * Tolerates legacy lowercase ('active', 'dropped', 'completed') and canonical uppercase
 * without altering historical records in the database.
 */
@Converter(autoApply = true)
@Slf4j
public class EnrollmentStatusConverter implements AttributeConverter<EnrollmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(EnrollmentStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name(); // Canonical uppercase "ACTIVE", "DROPPED", "COMPLETED"
    }

    @Override
    public EnrollmentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return EnrollmentStatus.valueOf(dbData.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown EnrollmentStatus value in database: '{}'. Defaulting to null.", dbData);
            return null;
        }
    }
}
