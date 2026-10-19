package com.vatly1.example.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.vatly1.example.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * Các loại câu hỏi được hệ thống hỗ trợ chính thức.
 * Hợp đồng FE-BE thống nhất: Chỉ hỗ trợ MCQ_SINGLE (Trắc nghiệm 1 đáp án) và FILL_BLANK (Điền đáp án).
 */
public enum QuestionType {
    MCQ_SINGLE,
    FILL_BLANK;

    @JsonCreator
    public static QuestionType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toUpperCase();
        for (QuestionType type : QuestionType.values()) {
            if (type.name().equalsIgnoreCase(clean)) {
                return type;
            }
        }
        throw new CustomException(
                "Hệ thống chỉ hỗ trợ 2 dạng câu hỏi: Trắc nghiệm (MCQ_SINGLE) và Điền đáp án (FILL_BLANK). Loại '" + value + "' không được hỗ trợ",
                HttpStatus.BAD_REQUEST);
    }
}