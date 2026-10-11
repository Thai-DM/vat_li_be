package com.vatly1.example.utils;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

public final class AnswerNormalizationUtil {

    private AnswerNormalizationUtil() {
    }

    /**
     * Chuẩn hóa văn bản trả lời theo chuẩn xác định:
     * 1. Unicode NFC normalization (đảm bảo đồng nhất ký tự tiếng Việt dựng sẵn và tổ hợp).
     * 2. Thu gọn chuỗi khoảng trắng liên tiếp về đúng 1 khoảng trắng đơn.
     * 3. Loại bỏ khoảng trắng thừa ở đầu và cuối (trim).
     * 4. Chuyển toàn bộ về chữ thường (case-insensitive).
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        String compacted = nfc.trim().replaceAll("\\s+", " ");
        return compacted.toLowerCase(Locale.ROOT);
    }

    /**
     * So sánh chính xác giữa câu trả lời của sinh viên và một đáp án mẫu sau chuẩn hóa.
     */
    public static boolean matches(String studentAnswer, String acceptedAnswer) {
        if (studentAnswer == null || acceptedAnswer == null) {
            return false;
        }
        String normStudent = normalize(studentAnswer);
        String normAccepted = normalize(acceptedAnswer);
        return !normStudent.isEmpty() && !normAccepted.isEmpty() && normStudent.equals(normAccepted);
    }

    /**
     * So sánh câu trả lời của sinh viên với danh sách các đáp án được chấp nhận.
     */
    public static boolean isMatchAny(String studentAnswer, Collection<String> acceptedAnswers) {
        if (studentAnswer == null || acceptedAnswers == null || acceptedAnswers.isEmpty()) {
            return false;
        }
        String normStudent = normalize(studentAnswer);
        if (normStudent.isEmpty()) {
            return false;
        }
        for (String accepted : acceptedAnswers) {
            if (normStudent.equals(normalize(accepted))) {
                return true;
            }
        }
        return false;
    }
}
