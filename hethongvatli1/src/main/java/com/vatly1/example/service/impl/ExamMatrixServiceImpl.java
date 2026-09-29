package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.ExamType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ExamMatrixDTO;
import com.vatly1.example.model.dto.ExamMatrixDetailDTO;
import com.vatly1.example.model.dto.MatrixValidationItemDTO;
import com.vatly1.example.model.request.CreateExamMatrixDTO;
import com.vatly1.example.model.request.ExamMatrixDetailRequestDTO;
import com.vatly1.example.model.request.UpdateExamMatrixDTO;
import com.vatly1.example.model.response.MatrixValidationResultDTO;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamMatrixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExamMatrixServiceImpl implements IExamMatrixService {

    private final IExamMatrixRepository examMatrixRepository;
    private final IExamMatrixDetailRepository examMatrixDetailRepository;
    private final IExamRepository examRepository;
    private final ISubjectRepository subjectRepository;
    private final IClassRepository classRepository;
    private final TopicRepository topicRepository;
    private final QuestionBankRepository questionBankRepository;

    @Override
    public List<ExamMatrixDTO> getExamMatrices(UUID classId, UUID subjectId) {
        UUID effectiveSubjectId = subjectId;

        if (effectiveSubjectId == null && classId != null) {
            Class clazz = classRepository.findById(classId).orElse(null);
            if (clazz != null) {
                effectiveSubjectId = clazz.getSubjectId();
            }
        }

        List<ExamMatrix> matrices;
        if (effectiveSubjectId != null) {
            matrices = examMatrixRepository.findBySubjectIdOrderByCreatedAtDesc(effectiveSubjectId);
        } else {
            matrices = examMatrixRepository.findAllByOrderByCreatedAtDesc();
        }

        return matrices.stream()
                .map(this::toMatrixDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ExamMatrixDTO createExamMatrix(CreateExamMatrixDTO dto, UUID creatorId) {
        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new CustomException("Không tìm thấy môn học với ID: " + dto.getSubjectId(), HttpStatus.NOT_FOUND));

        ExamType examType = dto.getExamType() != null ? dto.getExamType() : ExamType.PRACTICE;
        BigDecimal totalPoints = dto.getTotalPoints() != null ? dto.getTotalPoints() : BigDecimal.valueOf(10.0);

        ExamMatrix matrix = ExamMatrix.builder()
                .subjectId(subject.getSubjectId())
                .matrixName(dto.getMatrixName().trim())
                .examType(examType)
                .description(dto.getDescription())
                .totalPoints(totalPoints)
                .build();

        ExamMatrix savedMatrix = examMatrixRepository.save(matrix);

        // Lưu chi tiết ma trận
        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                Topic topic = topicRepository.findById(detailDto.getTopicId())
                        .orElseThrow(() -> new CustomException("Không tìm thấy chủ đề (Topic) với ID: " + detailDto.getTopicId(), HttpStatus.NOT_FOUND));

                if (!Objects.equals(topic.getSubjectId(), subject.getSubjectId())) {
                    throw new CustomException(String.format("Chủ đề '%s' không thuộc môn học '%s'", topic.getTopicName(), subject.getSubjectName()), HttpStatus.BAD_REQUEST);
                }

                ExamMatrixDetail detail = ExamMatrixDetail.builder()
                        .matrixId(savedMatrix.getMatrixId())
                        .topicId(topic.getTopicId())
                        .difficultyLevel(detailDto.getDifficultyLevel())
                        .numQuestions(detailDto.getNumQuestions())
                        .weightPercent(detailDto.getWeightPercent())
                        .build();

                examMatrixDetailRepository.save(detail);
            }
        }

        log.info("Exam matrix created successfully: {} (ID: {}) by user: {}", savedMatrix.getMatrixName(), savedMatrix.getMatrixId(), creatorId);
        return toMatrixDTO(savedMatrix);
    }

    @Override
    public ExamMatrixDTO getExamMatrixById(UUID matrixId) {
        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));
        return toMatrixDTO(matrix);
    }

    @Override
    @Transactional
    public ExamMatrixDTO updateExamMatrix(UUID matrixId, UpdateExamMatrixDTO dto, UUID updaterId) {
        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        if (dto.getMatrixName() != null && !dto.getMatrixName().isBlank()) {
            matrix.setMatrixName(dto.getMatrixName().trim());
        }
        if (dto.getExamType() != null) {
            matrix.setExamType(dto.getExamType());
        }
        if (dto.getDescription() != null) {
            matrix.setDescription(dto.getDescription());
        }
        if (dto.getTotalPoints() != null) {
            matrix.setTotalPoints(dto.getTotalPoints());
        }

        // Cập nhật lại chi tiết ma trận nếu có gửi lên
        if (dto.getDetails() != null) {
            // Kiểm tra các topic
            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                Topic topic = topicRepository.findById(detailDto.getTopicId())
                        .orElseThrow(() -> new CustomException("Không tìm thấy chủ đề (Topic) với ID: " + detailDto.getTopicId(), HttpStatus.NOT_FOUND));

                if (!Objects.equals(topic.getSubjectId(), matrix.getSubjectId())) {
                    throw new CustomException(String.format("Chủ đề '%s' không thuộc môn học của ma trận này", topic.getTopicName()), HttpStatus.BAD_REQUEST);
                }
            }

            // Xóa chi tiết cũ và tạo lại
            examMatrixDetailRepository.deleteByMatrixId(matrixId);

            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                ExamMatrixDetail detail = ExamMatrixDetail.builder()
                        .matrixId(matrix.getMatrixId())
                        .topicId(detailDto.getTopicId())
                        .difficultyLevel(detailDto.getDifficultyLevel())
                        .numQuestions(detailDto.getNumQuestions())
                        .weightPercent(detailDto.getWeightPercent())
                        .build();

                examMatrixDetailRepository.save(detail);
            }
        }

        ExamMatrix updated = examMatrixRepository.save(matrix);
        log.info("Exam matrix updated: {} (ID: {}) by user: {}", updated.getMatrixName(), updated.getMatrixId(), updaterId);
        return toMatrixDTO(updated);
    }

    @Override
    @Transactional
    public void deleteExamMatrix(UUID matrixId, UUID deleterId) {
        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        // Kiểm tra xem ma trận đã được gán vào kỳ thi nào chưa
        if (examRepository.existsByMatrixId(matrixId)) {
            throw new CustomException("Ma trận đề đang được liên kết với một hoặc nhiều kỳ thi, không thể xóa!", HttpStatus.BAD_REQUEST);
        }

        examMatrixDetailRepository.deleteByMatrixId(matrixId);
        examMatrixRepository.delete(matrix);
        log.info("Exam matrix deleted: {} (ID: {}) by user: {}", matrix.getMatrixName(), matrixId, deleterId);
    }

    @Override
    public MatrixValidationResultDTO validateExamMatrix(UUID matrixId) {
        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrixId);
        List<MatrixValidationItemDTO> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int totalRequired = 0;
        long totalAvailable = 0;
        boolean allSufficient = true;

        for (ExamMatrixDetail detail : details) {
            Topic topic = topicRepository.findById(detail.getTopicId()).orElse(null);
            String topicName = topic != null ? topic.getTopicName() : "Không xác định";

            long available = questionBankRepository.countByTopicIdAndDifficultyLevel(detail.getTopicId(), detail.getDifficultyLevel());
            int required = detail.getNumQuestions() != null ? detail.getNumQuestions() : 0;
            boolean isSufficient = available >= required;

            totalRequired += required;
            totalAvailable += available;

            if (!isSufficient) {
                allSufficient = false;
                warnings.add(String.format("Chủ đề '%s' (Mức độ %s): Cần %d câu nhưng ngân hàng chỉ có %d câu.",
                        topicName, detail.getDifficultyLevel(), required, available));
            }

            items.add(MatrixValidationItemDTO.builder()
                    .topicId(detail.getTopicId())
                    .topicName(topicName)
                    .difficultyLevel(detail.getDifficultyLevel())
                    .requiredQuestions(required)
                    .availableQuestions(available)
                    .isSufficient(isSufficient)
                    .build());
        }

        return MatrixValidationResultDTO.builder()
                .matrixId(matrixId)
                .matrixName(matrix.getMatrixName())
                .isValid(allSufficient)
                .totalRequired(totalRequired)
                .totalAvailable(totalAvailable)
                .items(items)
                .warnings(warnings)
                .build();
    }

    private ExamMatrixDTO toMatrixDTO(ExamMatrix matrix) {
        Subject subject = matrix.getSubjectId() != null ? subjectRepository.findById(matrix.getSubjectId()).orElse(null) : null;
        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrix.getMatrixId());

        List<ExamMatrixDetailDTO> detailDTOs = new ArrayList<>();
        int totalQuestions = 0;

        // Thống kê phân bố theo chủ đề và mức độ nhận thức
        Map<String, Integer> topicCountMap = new LinkedHashMap<>();
        Map<String, Integer> difficultyCountMap = new LinkedHashMap<>();

        // Khởi tạo trước các mức độ khó
        for (DifficultyLevel level : DifficultyLevel.values()) {
            difficultyCountMap.put(level.name(), 0);
        }

        for (ExamMatrixDetail d : details) {
            Topic topic = topicRepository.findById(d.getTopicId()).orElse(null);
            String topicName = topic != null ? topic.getTopicName() : "Chủ đề #" + d.getTopicId();
            int num = d.getNumQuestions() != null ? d.getNumQuestions() : 0;

            totalQuestions += num;

            // Cộng dồn theo chủ đề
            topicCountMap.put(topicName, topicCountMap.getOrDefault(topicName, 0) + num);

            // Cộng dồn theo độ khó
            if (d.getDifficultyLevel() != null) {
                String diffKey = d.getDifficultyLevel().name();
                difficultyCountMap.put(diffKey, difficultyCountMap.getOrDefault(diffKey, 0) + num);
            }

            detailDTOs.add(ExamMatrixDetailDTO.builder()
                    .detailId(d.getDetailId())
                    .matrixId(d.getMatrixId())
                    .topicId(d.getTopicId())
                    .topicName(topicName)
                    .difficultyLevel(d.getDifficultyLevel())
                    .numQuestions(d.getNumQuestions())
                    .weightPercent(d.getWeightPercent())
                    .build());
        }

        // Tạo cấu trúc statistics
        Map<String, Object> statistics = new HashMap<>();

        final int finalTotal = Math.max(totalQuestions, 1);
        List<Map<String, Object>> topicDist = topicCountMap.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("topicName", e.getKey());
                    map.put("questionCount", e.getValue());
                    map.put("percentage", BigDecimal.valueOf((e.getValue() * 100.0) / finalTotal).setScale(1, RoundingMode.HALF_UP));
                    return map;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> diffDist = difficultyCountMap.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("difficultyLevel", e.getKey());
                    map.put("questionCount", e.getValue());
                    map.put("percentage", BigDecimal.valueOf((e.getValue() * 100.0) / finalTotal).setScale(1, RoundingMode.HALF_UP));
                    return map;
                })
                .collect(Collectors.toList());

        statistics.put("difficultyDistribution", difficultyCountMap);
        statistics.put("topicDistribution", topicCountMap);
        statistics.put("byTopic", topicDist);
        statistics.put("byDifficulty", diffDist);

        return ExamMatrixDTO.builder()
                .matrixId(matrix.getMatrixId())
                .subjectId(matrix.getSubjectId())
                .subjectCode(subject != null ? subject.getSubjectCode() : null)
                .subjectName(subject != null ? subject.getSubjectName() : null)
                .matrixName(matrix.getMatrixName() != null ? matrix.getMatrixName() : matrix.getDescription())
                .examType(matrix.getExamType())
                .description(matrix.getDescription())
                .totalPoints(matrix.getTotalPoints() != null ? matrix.getTotalPoints() : BigDecimal.valueOf(10.0))
                .totalQuestions(totalQuestions)
                .createdAt(matrix.getCreatedAt())
                .details(detailDTOs)
                .statistics(statistics)
                .build();
    }
}
