package com.vatly1.example.service;

import com.vatly1.example.dto.CreateQuestionDTO;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface IPdfQuestionParserService {

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

    ParsedResult parseQuestionsFromPdf(InputStream inputStream, UUID subjectId, UUID topicId);
}
