package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ExamMatrix;
import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.enums.ApprovalStatus;
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
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.MatrixStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
import com.vatly1.example.repository.IExamMatrixDetailRepository;
import com.vatly1.example.repository.IExamMatrixRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExamMatrixService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
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
    private final ICloRepository cloRepository;
    private final IQuestionCloRepository questionCloRepository;
    private final IUserRepository userRepository;
    private final com.vatly1.example.service.algorithm.CloAwareQuestionMatcher cloAwareQuestionMatcher;

    @PersistenceContext
    private EntityManager entityManager;

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

    private void checkAdminRole(UUID userId) {
        if (userId == null) {
            throw new CustomException("Yêu cầu xác thực người dùng", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public ExamMatrixDTO createFinalExamMatrix(CreateExamMatrixDTO dto, UUID creatorId) {
        checkAdminRole(creatorId);
        dto.setExamType(ExamType.FINAL);
        return createExamMatrix(dto, creatorId);
    }

    @Override
    @Transactional
    public ExamMatrixDTO createExamMatrix(CreateExamMatrixDTO dto, UUID creatorId) {
        ExamType examType = dto.getExamType() != null ? dto.getExamType() : ExamType.PRACTICE;

        // Nếu tạo ma trận loại FINAL thì chỉ ADMIN mới được phép tạo
        if (examType == ExamType.FINAL) {
            checkAdminRole(creatorId);
        }

        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new CustomException("Không tìm thấy môn học với ID: " + dto.getSubjectId(), HttpStatus.NOT_FOUND));

        if (examType == ExamType.FINAL && (dto.getDetails() == null || dto.getDetails().isEmpty())) {
            throw new CustomException("Ma trận đề thi cuối kỳ (FINAL) phải có ít nhất một chi tiết (details)", HttpStatus.BAD_REQUEST);
        }

        BigDecimal totalPoints = dto.getTotalPoints() != null ? dto.getTotalPoints() : BigDecimal.valueOf(10.0);

        ExamMatrix matrix = ExamMatrix.builder()
                .matrixFamilyId(UUID.randomUUID())
                .subjectId(subject.getSubjectId())
                .matrixName(dto.getMatrixName().trim())
                .examType(examType)
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .revisionNumber(1)
                .rowVersion(0L)
                .description(dto.getDescription())
                .totalPoints(totalPoints)
                .createdBy(creatorId)
                .build();

        ExamMatrix savedMatrix = examMatrixRepository.save(matrix);

        // Lưu chi tiết ma trận
        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                if (detailDto.getNumQuestions() != null && detailDto.getNumQuestions() <= 0) {
                    throw new CustomException("Số lượng câu hỏi (numQuestions) phải lớn hơn 0", HttpStatus.BAD_REQUEST);
                }

                Topic topic = topicRepository.findById(detailDto.getTopicId())
                        .orElseThrow(() -> new CustomException("Không tìm thấy chủ đề (Topic) với ID: " + detailDto.getTopicId(), HttpStatus.NOT_FOUND));

                if (!Objects.equals(topic.getSubjectId(), subject.getSubjectId())) {
                    throw new CustomException(String.format("Chủ đề '%s' không thuộc môn học '%s'", topic.getTopicName(), subject.getSubjectName()), HttpStatus.BAD_REQUEST);
                }

                if (detailDto.getCloId() != null) {
                    Clo clo = cloRepository.findById(detailDto.getCloId())
                            .orElseThrow(() -> new CustomException("Không tìm thấy chuẩn đầu ra (CLO) với ID: " + detailDto.getCloId(), HttpStatus.NOT_FOUND));

                    if (!Objects.equals(clo.getSubjectId(), subject.getSubjectId())) {
                        throw new CustomException(String.format("Chuẩn đầu ra (CLO) '%s' không thuộc môn học '%s'", clo.getCloCode(), subject.getSubjectName()), HttpStatus.BAD_REQUEST);
                    }

                    if (Boolean.FALSE.equals(clo.getIsActive())) {
                        throw new CustomException(String.format("Chuẩn đầu ra (CLO) '%s' đã ngừng hoạt động (inactive), không thể đưa vào ma trận", clo.getCloCode()), HttpStatus.BAD_REQUEST);
                    }
                }

                ExamMatrixDetail detail = ExamMatrixDetail.builder()
                        .matrixId(savedMatrix.getMatrixId())
                        .subjectId(savedMatrix.getSubjectId())
                        .topicId(topic.getTopicId())
                        .cloId(detailDto.getCloId())
                        .difficultyLevel(detailDto.getDifficultyLevel())
                        .numQuestions(detailDto.getNumQuestions())
                        .weightPercent(detailDto.getWeightPercent() != null ? detailDto.getWeightPercent() : BigDecimal.ONE)
                        .build();

                examMatrixDetailRepository.save(detail);
            }
        }

        log.info("Exam matrix created successfully: {} (ID: {}, Family: {}) by user: {}",
                savedMatrix.getMatrixName(), savedMatrix.getMatrixId(), savedMatrix.getMatrixFamilyId(), creatorId);
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

        if (matrix.getExamType() == ExamType.FINAL) {
            checkAdminRole(updaterId);
        }

        if (Boolean.TRUE.equals(matrix.getIsLocked()) || matrix.getStatus() != MatrixStatus.DRAFT) {
            throw new CustomException("Ma trận đề thi đã ở trạng thái " + matrix.getStatus() + " (đã khóa), không thể chỉnh sửa!", HttpStatus.BAD_REQUEST);
        }

        // Force optimistic lock increment on parent entity to protect concurrency on parent and details
        entityManager.lock(matrix, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        if (dto.getMatrixName() != null && !dto.getMatrixName().isBlank()) {
            matrix.setMatrixName(dto.getMatrixName().trim());
        }
        if (dto.getExamType() != null) {
            if (dto.getExamType() == ExamType.FINAL && matrix.getExamType() != ExamType.FINAL) {
                checkAdminRole(updaterId);
            }
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
            if (matrix.getExamType() == ExamType.FINAL && dto.getDetails().isEmpty()) {
                throw new CustomException("Ma trận đề thi cuối kỳ phải có ít nhất một chi tiết (details)", HttpStatus.BAD_REQUEST);
            }
            // Kiểm tra các topic và CLO
            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                if (detailDto.getNumQuestions() != null && detailDto.getNumQuestions() <= 0) {
                    throw new CustomException("Số lượng câu hỏi (numQuestions) phải lớn hơn 0", HttpStatus.BAD_REQUEST);
                }

                Topic topic = topicRepository.findById(detailDto.getTopicId())
                        .orElseThrow(() -> new CustomException("Không tìm thấy chủ đề (Topic) với ID: " + detailDto.getTopicId(), HttpStatus.NOT_FOUND));

                if (!Objects.equals(topic.getSubjectId(), matrix.getSubjectId())) {
                    throw new CustomException(String.format("Chủ đề '%s' không thuộc môn học của ma trận này", topic.getTopicName()), HttpStatus.BAD_REQUEST);
                }

                if (detailDto.getCloId() != null) {
                    Clo clo = cloRepository.findById(detailDto.getCloId())
                            .orElseThrow(() -> new CustomException("Không tìm thấy chuẩn đầu ra (CLO) với ID: " + detailDto.getCloId(), HttpStatus.NOT_FOUND));

                    if (!Objects.equals(clo.getSubjectId(), matrix.getSubjectId())) {
                        throw new CustomException(String.format("Chuẩn đầu ra (CLO) '%s' không thuộc môn học của ma trận này", clo.getCloCode()), HttpStatus.BAD_REQUEST);
                    }

                    if (Boolean.FALSE.equals(clo.getIsActive())) {
                        throw new CustomException(String.format("Chuẩn đầu ra (CLO) '%s' đã ngừng hoạt động (inactive), không thể đưa vào ma trận", clo.getCloCode()), HttpStatus.BAD_REQUEST);
                    }
                }
            }

            // Xóa chi tiết cũ và tạo lại
            examMatrixDetailRepository.deleteByMatrixId(matrixId);

            for (ExamMatrixDetailRequestDTO detailDto : dto.getDetails()) {
                ExamMatrixDetail detail = ExamMatrixDetail.builder()
                        .matrixId(matrix.getMatrixId())
                        .subjectId(matrix.getSubjectId())
                        .topicId(detailDto.getTopicId())
                        .cloId(detailDto.getCloId())
                        .difficultyLevel(detailDto.getDifficultyLevel())
                        .numQuestions(detailDto.getNumQuestions())
                        .weightPercent(detailDto.getWeightPercent() != null ? detailDto.getWeightPercent() : BigDecimal.ONE)
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

        if (matrix.getExamType() == ExamType.FINAL) {
            checkAdminRole(deleterId);
        }

        if (Boolean.TRUE.equals(matrix.getIsLocked()) || matrix.getStatus() != MatrixStatus.DRAFT) {
            throw new CustomException("Ma trận đề thi đã ở trạng thái " + matrix.getStatus() + " (đã khóa), không được phép xóa!", HttpStatus.BAD_REQUEST);
        }

        // Kiểm tra xem ma trận đã được gán vào kỳ thi nào chưa
        if (examRepository.existsByMatrixId(matrixId)) {
            throw new CustomException("Ma trận đề đang được liên kết với một hoặc nhiều kỳ thi, không thể xóa!", HttpStatus.BAD_REQUEST);
        }

        examMatrixDetailRepository.deleteByMatrixId(matrixId);
        examMatrixRepository.delete(matrix);
        log.info("Exam matrix deleted: {} (ID: {}) by user: {}", matrix.getMatrixName(), matrixId, deleterId);
    }

    @Override
    @Transactional
    public ExamMatrixDTO publishMatrix(UUID matrixId, UUID adminId) {
        checkAdminRole(adminId);

        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        if (matrix.getExamType() != ExamType.FINAL) {
            throw new CustomException("Chỉ áp dụng quy trình công bố (publish) cho ma trận đề thi cuối kỳ (FINAL)", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() == MatrixStatus.ARCHIVED) {
            throw new CustomException("Ma trận đề thi đã bị lưu trữ (ARCHIVED), không thể công bố!", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() == MatrixStatus.PUBLISHED || Boolean.TRUE.equals(matrix.getIsLocked())) {
            throw new CustomException("Ma trận đề thi đã được công bố trước đó (PUBLISHED)!", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() != MatrixStatus.DRAFT) {
            throw new CustomException("Chỉ ma trận ở trạng thái DRAFT mới có thể công bố!", HttpStatus.BAD_REQUEST);
        }

        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrixId);
        if (details == null || details.isEmpty()) {
            throw new CustomException("Ma trận đề thi không có cấu hình chi tiết (details), không thể công bố!", HttpStatus.BAD_REQUEST);
        }

        // Kiểm tra tính khả dụng của ngân hàng câu hỏi bằng Max-Flow
        MatrixValidationResultDTO validation = validateMatrixQuestionAvailability(matrixId);
        if (!validation.isValid()) {
            String warnMsg = validation.getWarnings() != null && !validation.getWarnings().isEmpty()
                    ? String.join("; ", validation.getWarnings())
                    : "Ngân hàng câu hỏi không đủ câu hỏi APPROVED khả dụng";
            throw new CustomException("Không thể công bố ma trận đề thi: Ngân hàng câu hỏi không đủ câu hỏi APPROVED đáp ứng ma trận. Chi tiết: " + warnMsg, HttpStatus.BAD_REQUEST);
        }

        // Lock optimistic force increment to ensure safe state transition without concurrent overwrite
        entityManager.lock(matrix, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        matrix.setStatus(MatrixStatus.PUBLISHED);
        matrix.setIsLocked(true);

        ExamMatrix published = examMatrixRepository.save(matrix);
        log.info("Exam matrix PUBLISHED successfully: {} (ID: {}, Rev: {}) by admin: {}",
                published.getMatrixName(), published.getMatrixId(), published.getRevisionNumber(), adminId);
        return toMatrixDTO(published);
    }

    @Override
    @Transactional
    public ExamMatrixDTO archiveMatrix(UUID matrixId, UUID adminId) {
        checkAdminRole(adminId);

        ExamMatrix matrix = examMatrixRepository.findByIdForUpdate(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        if (matrix.getStatus() == MatrixStatus.DRAFT) {
            throw new CustomException("Không thể lưu trữ trực tiếp ma trận đang ở trạng thái DRAFT. Chỉ ma trận PUBLISHED mới được lưu trữ!", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() == MatrixStatus.ARCHIVED) {
            throw new CustomException("Ma trận đề thi đã ở trạng thái lưu trữ (ARCHIVED)!", HttpStatus.BAD_REQUEST);
        }

        if (matrix.getStatus() != MatrixStatus.PUBLISHED) {
            throw new CustomException("Chỉ ma trận ở trạng thái PUBLISHED mới được phép lưu trữ (ARCHIVED)!", HttpStatus.BAD_REQUEST);
        }

        entityManager.lock(matrix, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        matrix.setStatus(MatrixStatus.ARCHIVED);
        matrix.setIsLocked(true);

        ExamMatrix archived = examMatrixRepository.save(matrix);
        log.info("Exam matrix ARCHIVED successfully: {} (ID: {}, Rev: {}) by admin: {}",
                archived.getMatrixName(), archived.getMatrixId(), archived.getRevisionNumber(), adminId);
        return toMatrixDTO(archived);
    }

    @Override
    @Transactional
    public ExamMatrixDTO cloneMatrixRevision(UUID matrixId, UUID adminId) {
        checkAdminRole(adminId);

        ExamMatrix source = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi nguồn với ID: " + matrixId, HttpStatus.NOT_FOUND));

        if (source.getExamType() != ExamType.FINAL) {
            throw new CustomException("Chỉ hỗ trợ clone revision cho ma trận đề thi cuối kỳ (FINAL)", HttpStatus.BAD_REQUEST);
        }

        // Lock family records in DB to serialize concurrent revision increments
        examMatrixRepository.findByMatrixFamilyIdForUpdate(source.getMatrixFamilyId());

        Integer maxRev = examMatrixRepository.findMaxRevisionNumberByMatrixFamilyId(source.getMatrixFamilyId());
        int newRevisionNumber = (maxRev != null ? maxRev : 0) + 1;

        ExamMatrix newMatrix = ExamMatrix.builder()
                .matrixFamilyId(source.getMatrixFamilyId())
                .subjectId(source.getSubjectId())
                .matrixName(source.getMatrixName() != null ? source.getMatrixName() + " (Rev " + newRevisionNumber + ")" : "Final Matrix (Rev " + newRevisionNumber + ")")
                .examType(source.getExamType())
                .status(MatrixStatus.DRAFT)
                .isLocked(false)
                .revisionNumber(newRevisionNumber)
                .rowVersion(0L)
                .description(source.getDescription())
                .totalPoints(source.getTotalPoints())
                .createdBy(adminId)
                .build();

        ExamMatrix savedNewMatrix = examMatrixRepository.save(newMatrix);

        List<ExamMatrixDetail> sourceDetails = examMatrixDetailRepository.findByMatrixId(source.getMatrixId());
        if (sourceDetails != null && !sourceDetails.isEmpty()) {
            for (ExamMatrixDetail sd : sourceDetails) {
                ExamMatrixDetail clonedDetail = ExamMatrixDetail.builder()
                        .matrixId(savedNewMatrix.getMatrixId())
                        .subjectId(savedNewMatrix.getSubjectId())
                        .topicId(sd.getTopicId())
                        .cloId(sd.getCloId())
                        .difficultyLevel(sd.getDifficultyLevel())
                        .numQuestions(sd.getNumQuestions())
                        .weightPercent(sd.getWeightPercent())
                        .build();
                examMatrixDetailRepository.save(clonedDetail);
            }
        }

        log.info("Exam matrix revision CLONED successfully from {} (rev {}) to {} (rev {}) by admin: {}",
                source.getMatrixId(), source.getRevisionNumber(), savedNewMatrix.getMatrixId(), savedNewMatrix.getRevisionNumber(), adminId);

        return toMatrixDTO(savedNewMatrix);
    }

    @Override
    public List<ExamMatrixDTO> getMatricesBySubject(UUID subjectId) {
        List<ExamMatrix> matrices;
        if (subjectId != null) {
            matrices = examMatrixRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId);
        } else {
            matrices = examMatrixRepository.findAllByOrderByCreatedAtDesc();
        }
        return matrices.stream().map(this::toMatrixDTO).collect(Collectors.toList());
    }

    @Override
    public MatrixValidationResultDTO validateExamMatrix(UUID matrixId) {
        return validateMatrixQuestionAvailability(matrixId);
    }

    @Override
    public MatrixValidationResultDTO validateMatrixQuestionAvailability(UUID matrixId) {
        ExamMatrix matrix = examMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ma trận đề thi với ID: " + matrixId, HttpStatus.NOT_FOUND));

        List<ExamMatrixDetail> details = examMatrixDetailRepository.findByMatrixId(matrixId);
        if (details == null || details.isEmpty()) {
            return MatrixValidationResultDTO.builder()
                    .matrixId(matrixId)
                    .matrixName(matrix.getMatrixName())
                    .isValid(false)
                    .totalRequired(0)
                    .totalAvailable(0)
                    .items(Collections.emptyList())
                    .warnings(List.of("Ma trận đề thi không có cấu hình chi tiết (details)."))
                    .build();
        }

        if (matrix.getExamType() == ExamType.FINAL) {
            return validateFinalMatrix(matrix, details);
        } else {
            return validatePracticeMatrix(matrix, details);
        }
    }

    private MatrixValidationResultDTO validateFinalMatrix(ExamMatrix matrix, List<ExamMatrixDetail> details) {
        Set<UUID> topicIds = details.stream()
                .map(ExamMatrixDetail::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<QuestionBank> candidates = topicIds.isEmpty() ? Collections.emptyList() :
                questionBankRepository.findBySubjectIdAndTopicIdInAndApprovalStatus(matrix.getSubjectId(), topicIds, ApprovalStatus.APPROVED);

        Set<UUID> candidateIds = candidates.stream()
                .map(QuestionBank::getQuestionId)
                .collect(Collectors.toSet());

        List<QuestionClo> questionClos = candidateIds.isEmpty() ? Collections.emptyList() :
                questionCloRepository.findBySubjectIdAndQuestionIdIn(matrix.getSubjectId(), candidateIds);

        Map<UUID, Set<UUID>> questionCloMap = new HashMap<>();
        for (QuestionClo qc : questionClos) {
            questionCloMap.computeIfAbsent(qc.getQuestionId(), k -> new HashSet<>()).add(qc.getCloId());
        }

        com.vatly1.example.service.algorithm.CloAwareQuestionMatcher.MatchResult matchResult =
                cloAwareQuestionMatcher.match(details, candidates, questionCloMap, null);

        List<MatrixValidationItemDTO> items = new ArrayList<>();
        for (ExamMatrixDetail detail : details) {
            Topic topic = topicRepository.findById(detail.getTopicId()).orElse(null);
            String topicName = topic != null ? topic.getTopicName() : "Không xác định";
            int required = detail.getNumQuestions() != null ? detail.getNumQuestions() : 0;
            int eligible = matchResult.getEligibleCandidateCount(detail);
            int matched = matchResult.getMatchedCount(detail);
            boolean sufficient = matched >= required && required >= 0;

            items.add(MatrixValidationItemDTO.builder()
                    .topicId(detail.getTopicId())
                    .topicName(topicName)
                    .difficultyLevel(detail.getDifficultyLevel())
                    .requiredQuestions(required)
                    .availableQuestions(eligible)
                    .exactAvailable(eligible)
                    .fallbackAvailable(0)
                    .fallbackNeeded(0)
                    .isExactSufficient(sufficient)
                    .isSufficient(sufficient)
                    .build());
        }

        return MatrixValidationResultDTO.builder()
                .matrixId(matrix.getMatrixId())
                .matrixName(matrix.getMatrixName())
                .isValid(matchResult.isFeasible())
                .totalRequired(matchResult.getTotalRequiredQuestions())
                .totalAvailable(matchResult.getTotalMatchedQuestions())
                .items(items)
                .warnings(matchResult.getDiagnostics())
                .build();
    }

    private MatrixValidationResultDTO validatePracticeMatrix(ExamMatrix matrix, List<ExamMatrixDetail> details) {
        List<MatrixValidationItemDTO> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Mô phỏng chính xác thuật toán sinh đề với cơ chế chống trùng lặp câu hỏi (claimedQuestionIds)
        Set<UUID> claimedQuestionIds = new HashSet<>();
        int totalRequired = 0;
        long totalAvailable = 0;
        boolean allSufficient = true;

        for (ExamMatrixDetail detail : details) {
            Topic topic = topicRepository.findById(detail.getTopicId()).orElse(null);
            String topicName = topic != null ? topic.getTopicName() : "Không xác định";

            int required = detail.getNumQuestions() != null ? detail.getNumQuestions() : 0;
            totalRequired += required;

            // 1. Truy vấn câu hỏi APPROVED đúng topic và độ khó
            List<QuestionBank> exactApproved = questionBankRepository.findByTopicIdAndDifficultyLevelAndApprovalStatus(
                    detail.getTopicId(), detail.getDifficultyLevel(), ApprovalStatus.APPROVED);

            List<QuestionBank> availableExact = exactApproved.stream()
                    .filter(q -> !claimedQuestionIds.contains(q.getQuestionId()))
                    .collect(Collectors.toList());

            int exactCount = availableExact.size();
            int usedExact;
            int usedFallback = 0;
            int neededFallback = 0;
            boolean isSufficient;
            long availableForThisDetail;

            if (exactCount >= required) {
                // Đủ câu hỏi đúng độ khó
                usedExact = required;
                for (int i = 0; i < required; i++) {
                    claimedQuestionIds.add(availableExact.get(i).getQuestionId());
                }
                isSufficient = true;
                availableForThisDetail = exactCount;
                totalAvailable += usedExact;
            } else {
                // Thiếu câu đúng độ khó -> Dùng toàn bộ câu đúng độ khó khả dụng
                usedExact = exactCount;
                for (QuestionBank q : availableExact) {
                    claimedQuestionIds.add(q.getQuestionId());
                }
                neededFallback = required - usedExact;

                // 2. Truy vấn fallback: Các câu APPROVED khác trong cùng Topic (loại trừ đã claimed)
                List<QuestionBank> topicApproved = questionBankRepository.findByTopicIdAndApprovalStatus(
                        detail.getTopicId(), ApprovalStatus.APPROVED);

                List<QuestionBank> availableFallback = topicApproved.stream()
                        .filter(q -> !claimedQuestionIds.contains(q.getQuestionId()))
                        .collect(Collectors.toList());

                int fallbackCount = availableFallback.size();

                if (fallbackCount >= neededFallback) {
                    // Đủ bù từ độ khó khác
                    usedFallback = neededFallback;
                    for (int i = 0; i < neededFallback; i++) {
                        claimedQuestionIds.add(availableFallback.get(i).getQuestionId());
                    }
                    isSufficient = true;
                    availableForThisDetail = usedExact + usedFallback;
                    totalAvailable += availableForThisDetail;

                    warnings.add(String.format("Chủ đề '%s' (Mức độ %s): Cần %d câu nhưng chỉ có %d câu đúng độ khó đã duyệt. Cần bù %d câu từ độ khó khác trong cùng chủ đề.",
                            topicName, detail.getDifficultyLevel(), required, usedExact, usedFallback));
                } else {
                    // Kể cả bù fallback vẫn không đủ câu APPROVED
                    usedFallback = fallbackCount;
                    for (QuestionBank q : availableFallback) {
                        claimedQuestionIds.add(q.getQuestionId());
                    }
                    isSufficient = false;
                    allSufficient = false;
                    availableForThisDetail = usedExact + usedFallback;
                    totalAvailable += availableForThisDetail;

                    warnings.add(String.format("Chủ đề '%s' (Mức độ %s): Cần %d câu nhưng chỉ có %d câu đã duyệt khả dụng (đúng độ khó: %d, bù từ độ khó khác: %d, thiếu: %d câu). Không thể sinh đề.",
                            topicName, detail.getDifficultyLevel(), required, availableForThisDetail, usedExact, usedFallback, required - (int) availableForThisDetail));
                }
            }

            // Kiểm tra số lượng câu chưa duyệt (DRAFT/PENDING/REJECTED) để cảnh báo cho giảng viên
            long totalInBank = questionBankRepository.countByTopicIdAndDifficultyLevel(detail.getTopicId(), detail.getDifficultyLevel());
            long unapprovedCount = totalInBank - exactApproved.size();
            if (unapprovedCount > 0) {
                warnings.add(String.format("Chủ đề '%s' (Mức độ %s): Có %d câu hỏi chưa được phê duyệt (DRAFT/PENDING/REJECTED) bị loại khỏi quá trình sinh đề.",
                        topicName, detail.getDifficultyLevel(), unapprovedCount));
            }

            items.add(MatrixValidationItemDTO.builder()
                    .topicId(detail.getTopicId())
                    .topicName(topicName)
                    .difficultyLevel(detail.getDifficultyLevel())
                    .requiredQuestions(required)
                    .availableQuestions(availableForThisDetail)
                    .exactAvailable(usedExact)
                    .fallbackAvailable(usedFallback)
                    .fallbackNeeded(neededFallback)
                    .isExactSufficient(exactCount >= required)
                    .isSufficient(isSufficient)
                    .build());
        }

        return MatrixValidationResultDTO.builder()
                .matrixId(matrix.getMatrixId())
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

            String cloCode = null;
            if (d.getCloId() != null) {
                Clo clo = cloRepository.findById(d.getCloId()).orElse(null);
                if (clo != null) {
                    cloCode = clo.getCloCode();
                }
            }

            detailDTOs.add(ExamMatrixDetailDTO.builder()
                    .detailId(d.getDetailId())
                    .matrixId(d.getMatrixId())
                    .subjectId(d.getSubjectId())
                    .topicId(d.getTopicId())
                    .topicName(topicName)
                    .cloId(d.getCloId())
                    .cloCode(cloCode)
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
                .matrixFamilyId(matrix.getMatrixFamilyId())
                .subjectId(matrix.getSubjectId())
                .subjectCode(subject != null ? subject.getSubjectCode() : null)
                .subjectName(subject != null ? subject.getSubjectName() : null)
                .matrixName(matrix.getMatrixName() != null ? matrix.getMatrixName() : matrix.getDescription())
                .examType(matrix.getExamType())
                .matrixStatus(matrix.getStatus())
                .isLocked(matrix.getIsLocked())
                .revisionNumber(matrix.getRevisionNumber())
                .description(matrix.getDescription())
                .totalPoints(matrix.getTotalPoints() != null ? matrix.getTotalPoints() : BigDecimal.valueOf(10.0))
                .totalQuestions(totalQuestions)
                .createdAt(matrix.getCreatedAt())
                .createdBy(matrix.getCreatedBy())
                .details(detailDTOs)
                .statistics(statistics)
                .build();
    }
}
