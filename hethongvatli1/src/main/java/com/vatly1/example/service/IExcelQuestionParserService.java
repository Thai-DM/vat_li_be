package com.vatly1.example.service;

import com.vatly1.example.model.request.CreateQuestionDTO;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface IExcelQuestionParserService {

    class ParsedResult {
        private final List<CreateQuestionDTO> questions;
        private final List<String> warnings;

        public ParsedResult(List<CreateQuestionDTO> questions, List<String> warnings) {
            this.questions = questions;
            this.warnings = warnings;
        }

        public List<CreateQuestionDTO> getQuestions() {
            return questions;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    /**
     * Phân tích tệp bảng tính Excel (.xlsx, .xls) và trích xuất danh sách câu hỏi trắc nghiệm.
     */
    ParsedResult parseQuestionsFromExcel(InputStream inputStream, UUID subjectId, UUID topicId);

    /**
     * Tạo file Excel mẫu chuẩn (.xlsx) có sẵn tiêu đề và dữ liệu mẫu để người dùng tải về.
     */
    byte[] generateTemplate();
}