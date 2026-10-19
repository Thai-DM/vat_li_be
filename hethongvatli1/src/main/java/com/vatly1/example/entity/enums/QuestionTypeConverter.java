package com.vatly1.example.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA Converter for QuestionType.
 * Bridges QuestionType enum (MCQ_SINGLE, FILL_BLANK) with database storage.
 * Gracefully handles legacy or unsupported database values without crashing Hibernate entity loading.
 */
@Converter(autoApply = true)
@Slf4j
public class QuestionTypeConverter implements AttributeConverter<QuestionType, String> {

    @Override
    public String convertToDatabaseColumn(QuestionType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public QuestionType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String clean = dbData.trim().toUpperCase();
        try {
            return QuestionType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown or unsupported QuestionType in database: '{}'. Defaulting to MCQ_SINGLE for entity safety.", dbData);
            return QuestionType.MCQ_SINGLE;
        }
    }
}
