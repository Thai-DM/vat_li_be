package com.vatly1.example.service.impl;

import com.vatly1.example.dto.request.CreateQuestionDTO;
import com.vatly1.example.dto.dto.QuestionBankDTO;
import com.vatly1.example.dto.dto.QuestionOptionDTO;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IQuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vatly1.example.dto.response.QuestionImportResultDTO;
import com.vatly1.example.service.IExcelQuestionParserService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements IQuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final ISubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final IExcelQuestionParserService excelQuestionParserService;

    @Override
    public Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable) {
        Page<QuestionBank> questions;

        if (topicId != null && difficultyLevel != null) {
            questions = questionBankRepository.findBySubjectIdAndTopicIdAndDifficultyLevel(subjectId, topicId, difficultyLevel, pageable);
        } else if (topicId != null) {
            questions = questionBankRepository.findBySubjectIdAndTopicId(subjectId, topicId, pageable);
        } else if (difficultyLevel != null) {
            questions = questionBankRepository.findBySubjectIdAndDifficultyLevel(subjectId, difficultyLevel, pageable);
        } else {
            questions = questionBankRepository.findBySubjectId(subjectId, pageable);
        }

        return questions.map(this::mapToDTO);
    }

    @Override
    public QuestionBankDTO getQuestionById(UUID questionId) {
        QuestionBank question = questionBankRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));
        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO createQuestion(CreateQuestionDTO dto, UUID currentUserId) {
        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        if (!topicRepository.existsById(dto.getTopicId())) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }

        if (dto.getQuestionType() == QuestionType.MCQ_SINGLE && dto.getOptions() != null) {
            long correctCount = dto.getOptions().stream()
                    .filter(opt -> Boolean.TRUE.equals(opt.getIsCorrect()))
                    .count();
            if (correctCount != 1) {
                throw new CustomException("Câu hỏi trắc nghiệm một đáp án đúng (MCQ_SINGLE) phải có chính xác 1 đáp án đúng", HttpStatus.BAD_REQUEST);
            }
        }

        QuestionBank question = QuestionBank.builder()
                .subjectId(dto.getSubjectId())
                .topicId(dto.getTopicId())
                .questionType(dto.getQuestionType())
                .content(dto.getContent())
                .mediaUrl(dto.getMediaUrl())
                .difficultyLevel(dto.getDifficultyLevel())
                .cognitiveLevel(dto.getCognitiveLevel())
                .approvalStatus(ApprovalStatus.PENDING)
                .createdBy(currentUserId)
                .createdAt(Instant.now())
                .build();

        question = questionBankRepository.save(question);

        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            for (CreateQuestionDTO.CreateQuestionOptionDTO optDto : dto.getOptions()) {
                QuestionOption option = QuestionOption.builder()
                        .questionId(question.getQuestionId())
                        .optionText(optDto.getContent())
                        .isCorrect(optDto.getIsCorrect())
                        .orderIndex(optDto.getOrderIndex() != null ? optDto.getOrderIndex() : 0)
                        .build();
                questionOptionRepository.save(option);
            }
        }

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO updateQuestion(UUID questionId, CreateQuestionDTO dto, UUID currentUserId, String role) {
        QuestionBank question = questionBankRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(role) && !question.getCreatedBy().equals(currentUserId)) {
            throw new CustomException("You do not have permission to update this question", HttpStatus.FORBIDDEN);
        }

        question.setSubjectId(dto.getSubjectId());
        question.setTopicId(dto.getTopicId());
        question.setQuestionType(dto.getQuestionType());
        question.setContent(dto.getContent());
        question.setMediaUrl(dto.getMediaUrl());
        question.setDifficultyLevel(dto.getDifficultyLevel());
        question.setCognitiveLevel(dto.getCognitiveLevel());
        question.setApprovalStatus(ApprovalStatus.PENDING); // Needs re-approval

        question = questionBankRepository.save(question);

        // Replace all options
        questionOptionRepository.deleteByQuestionId(questionId);
        
        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            for (CreateQuestionDTO.CreateQuestionOptionDTO optDto : dto.getOptions()) {
                QuestionOption option = QuestionOption.builder()
                        .questionId(question.getQuestionId())
                        .optionText(optDto.getContent())
                        .isCorrect(optDto.getIsCorrect())
                        .orderIndex(optDto.getOrderIndex() != null ? optDto.getOrderIndex() : 0)
                        .build();
                questionOptionRepository.save(option);
            }
        }

        return mapToDTO(question);
    }

    @Override
    public QuestionBankDTO approveQuestion(UUID questionId) {
        QuestionBank question = questionBankRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        question.setApprovalStatus(ApprovalStatus.APPROVED);
        question = questionBankRepository.save(question);
        return mapToDTO(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(UUID questionId, UUID currentUserId, String role) {
        QuestionBank question = questionBankRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(role) && !question.getCreatedBy().equals(currentUserId)) {
            throw new CustomException("You do not have permission to delete this question", HttpStatus.FORBIDDEN);
        }

        questionOptionRepository.deleteByQuestionId(questionId);
        questionBankRepository.delete(question);
    }

    @Override
    @Transactional
    public QuestionImportResultDTO importQuestionsFromExcel(MultipartFile file, UUID subjectId, UUID topicId, UUID currentUserId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        if (!topicRepository.existsById(topicId)) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new CustomException("File Excel không được để trống", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls")) {
            throw new CustomException("Định dạng file không được hỗ trợ. Vui lòng tải lên file Excel (.xlsx hoặc .xls)", HttpStatus.BAD_REQUEST);
        }

        try (InputStream is = file.getInputStream()) {
            IExcelQuestionParserService.ParsedResult parsedResult = excelQuestionParserService.parseQuestionsFromExcel(is, subjectId, topicId);
            List<QuestionBankDTO> savedQuestions = new ArrayList<>();

            for (CreateQuestionDTO qDto : parsedResult.getQuestions()) {
                QuestionBankDTO saved = createQuestion(qDto, currentUserId);
                savedQuestions.add(saved);
            }

            return QuestionImportResultDTO.builder()
                    .totalParsed(parsedResult.getQuestions().size())
                    .totalImported(savedQuestions.size())
                    .questions(savedQuestions)
                    .warnings(parsedResult.getWarnings())
                    .build();
        } catch (IOException e) {
            throw new CustomException("Lỗi khi đọc file Excel: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public byte[] downloadQuestionExcelTemplate() {
        return excelQuestionParserService.generateTemplate();
    }

    private QuestionBankDTO mapToDTO(QuestionBank question) {
        List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(question.getQuestionId());
        
        List<QuestionOptionDTO> optionDTOs = options.stream().map(opt -> QuestionOptionDTO.builder()
                .optionId(opt.getOptionId())
                .questionId(opt.getQuestionId())
                .content(opt.getOptionText())
                .isCorrect(opt.getIsCorrect())
                .orderIndex(opt.getOrderIndex())
                .build()).collect(Collectors.toList());

        return QuestionBankDTO.builder()
                .questionId(question.getQuestionId())
                .subjectId(question.getSubjectId())
                .topicId(question.getTopicId())
                .questionType(question.getQuestionType())
                .content(question.getContent())
                .mediaUrl(question.getMediaUrl())
                .difficultyLevel(question.getDifficultyLevel())
                .cognitiveLevel(question.getCognitiveLevel())
                .approvalStatus(question.getApprovalStatus())
                .createdBy(question.getCreatedBy())
                .createdAt(question.getCreatedAt())
                .options(optionDTOs)
                .build();
    }
}