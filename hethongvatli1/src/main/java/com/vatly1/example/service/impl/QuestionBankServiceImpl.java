package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Clo;
import com.vatly1.example.entity.QuestionApproval;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.QuestionClo;
import com.vatly1.example.entity.QuestionOption;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.QuestionApprovalDTO;
import com.vatly1.example.model.dto.QuestionBankDTO;
import com.vatly1.example.model.dto.QuestionOptionDTO;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.model.response.QuestionImportResultDTO;
import com.vatly1.example.entity.QuestionAcceptedAnswer;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ICloRepository;
import com.vatly1.example.repository.IExamAnswerRepository;
import com.vatly1.example.repository.IExamQuestionRepository;
import com.vatly1.example.repository.IPracticeSessionQuestionRepository;
import com.vatly1.example.repository.IQuestionApprovalRepository;
import com.vatly1.example.repository.IQuestionCloRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.QuestionOptionRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IExcelQuestionParserService;
import com.vatly1.example.service.IQuestionBankService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements IQuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;
    private final ISubjectRepository subjectRepository;
    private final IChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final IExcelQuestionParserService excelQuestionParserService;
    private final IExamQuestionRepository examQuestionRepository;
    private final IExamAnswerRepository examAnswerRepository;
    private final ICloRepository cloRepository;
    private final IQuestionCloRepository questionCloRepository;
    private final IQuestionApprovalRepository questionApprovalRepository;
    private final IUserRepository userRepository;
    private final IClassRepository classRepository;
    private final IPracticeSessionQuestionRepository practiceSessionQuestionRepository;

    @Override
    public Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable) {
        return getQuestions(subjectId, null, topicId, difficultyLevel, pageable);
    }

    @Override
    public Page<QuestionBankDTO> getQuestions(UUID subjectId, UUID chapterId, UUID topicId, DifficultyLevel difficultyLevel, Pageable pageable) {
        if (chapterId != null) {
            Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
            if (chapter == null) {
                return Page.empty(pageable);
            }
            if (subjectId != null && !subjectId.equals(chapter.getSubjectId())) {
                return Page.empty(pageable);
            }
            if (topicId != null) {
                Topic topic = topicRepository.findById(topicId).orElse(null);
                if (topic == null || !chapterId.equals(topic.getChapterId()) || (subjectId != null && !subjectId.equals(topic.getSubjectId()))) {
                    return Page.empty(pageable);
                }
            } else {
                List<Topic> chapterTopics = topicRepository.findByChapterIdOrderByOrderIndexAsc(chapterId);
                if (chapterTopics.isEmpty()) {
                    return Page.empty(pageable);
                }
                List<UUID> topicIds = chapterTopics.stream().map(Topic::getTopicId).collect(Collectors.toList());
                Specification<QuestionBank> spec = (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (subjectId != null) {
                        predicates.add(cb.equal(root.get("subjectId"), subjectId));
                    }
                    predicates.add(root.get("topicId").in(topicIds));
                    if (difficultyLevel != null) {
                        predicates.add(cb.equal(root.get("difficultyLevel"), difficultyLevel));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
                return questionBankRepository.findAll(spec, pageable).map(this::mapToDTO);
            }
        }

        Specification<QuestionBank> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (topicId != null) {
                predicates.add(cb.equal(root.get("topicId"), topicId));
            }
            if (difficultyLevel != null) {
                predicates.add(cb.equal(root.get("difficultyLevel"), difficultyLevel));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<QuestionBank> questions = questionBankRepository.findAll(spec, pageable);
        return questions.map(this::mapToDTO);
    }

    @Override
    public List<QuestionBankDTO> getApprovedQuestionsBySubjectAndTopic(UUID subjectId, UUID topicId) {
        if (topicId == null) {
            return Collections.emptyList();
        }
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic == null) {
            return Collections.emptyList();
        }
        if (subjectId != null && !subjectId.equals(topic.getSubjectId())) {
            return Collections.emptyList();
        }
        List<QuestionBank> list = questionBankRepository.findByTopicIdAndApprovalStatus(topicId, ApprovalStatus.APPROVED);
        if (subjectId != null) {
            list = list.stream().filter(q -> subjectId.equals(q.getSubjectId())).collect(Collectors.toList());
        }
        return list.stream().map(this::mapToDTO).collect(Collectors.toList());
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
        return createQuestion(dto, currentUserId, null);
    }

    @Override
    @Transactional
    public QuestionBankDTO createQuestion(CreateQuestionDTO dto, UUID currentUserId, String role) {
        User resolvedUser = resolveCurrentUser(currentUserId);
        UUID effectiveUserId = resolvedUser != null ? resolvedUser.getUserId() : currentUserId;
        String effectiveRole = resolveRole(resolvedUser, role);

        if (effectiveUserId == null) {
            throw new CustomException("User must be authenticated to create questions", HttpStatus.UNAUTHORIZED);
        }

        checkSubjectPermission(dto.getSubjectId(), effectiveUserId, effectiveRole);

        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        Topic topic = topicRepository.findById(dto.getTopicId())
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(topic.getSubjectId(), dto.getSubjectId())) {
            throw new CustomException("Topic does not belong to the selected subject", HttpStatus.BAD_REQUEST);
        }

        if (dto.getQuestionType() != QuestionType.MCQ_SINGLE && dto.getQuestionType() != QuestionType.FILL_BLANK) {
            throw new CustomException("Hệ thống chỉ hỗ trợ 2 dạng câu hỏi: Trắc nghiệm (MCQ_SINGLE) và Điền đáp án (FILL_BLANK). Loại '" + dto.getQuestionType() + "' không được hỗ trợ", HttpStatus.BAD_REQUEST);
        }

        if (dto.getQuestionType() == QuestionType.FILL_BLANK) {
            List<String> cleanAnswers = extractAcceptedAnswers(dto);
            if (cleanAnswers.isEmpty()) {
                throw new CustomException("Câu hỏi điền đáp án (FILL_BLANK) phải có ít nhất 1 đáp án được chấp nhận", HttpStatus.BAD_REQUEST);
            }
        }

        if (dto.getQuestionType() == QuestionType.MCQ_SINGLE && dto.getOptions() != null) {
            long correctCount = dto.getOptions().stream()
                    .filter(opt -> Boolean.TRUE.equals(opt.getIsCorrect()))
                    .count();
            if (correctCount != 1) {
                throw new CustomException("Câu hỏi trắc nghiệm một đáp án đúng (MCQ_SINGLE) phải có chính xác 1 đáp án đúng", HttpStatus.BAD_REQUEST);
            }
        }

        if (dto.getApprovalStatus() == ApprovalStatus.APPROVED || dto.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw new CustomException("Không thể tạo câu hỏi trực tiếp với trạng thái " + dto.getApprovalStatus(), HttpStatus.BAD_REQUEST);
        }

        ApprovalStatus initialStatus = ApprovalStatus.DRAFT;
        boolean immediateSubmit = dto.getApprovalStatus() == ApprovalStatus.PENDING;
        if (immediateSubmit) {
            initialStatus = ApprovalStatus.PENDING;
        }

        QuestionBank question = QuestionBank.builder()
                .subjectId(dto.getSubjectId())
                .topicId(dto.getTopicId())
                .questionType(dto.getQuestionType())
                .content(dto.getContent())
                .mediaUrl(dto.getMediaUrl())
                .difficultyLevel(dto.getDifficultyLevel())
                .cognitiveLevel(dto.getCognitiveLevel())
                .approvalStatus(initialStatus)
                .createdBy(effectiveUserId)
                .createdAt(Instant.now())
                .build();

        question = questionBankRepository.save(question);

        if (dto.getQuestionType() == QuestionType.FILL_BLANK) {
            List<String> cleanAnswers = extractAcceptedAnswers(dto);
            int idx = 0;
            for (String ans : cleanAnswers) {
                QuestionAcceptedAnswer accepted = QuestionAcceptedAnswer.builder()
                        .questionId(question.getQuestionId())
                        .acceptedText(ans)
                        .orderIndex(idx++)
                        .build();
                questionAcceptedAnswerRepository.save(accepted);
            }
        } else if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
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

        if (dto.getCloIds() != null && !dto.getCloIds().isEmpty()) {
            syncQuestionClos(question, dto.getCloIds());
        }

        // Record Audit Trail
        recordApprovalAudit(question.getQuestionId(), effectiveUserId, "CREATE_DRAFT", null, ApprovalStatus.DRAFT, "Tạo bản nháp câu hỏi");
        if (immediateSubmit) {
            recordApprovalAudit(question.getQuestionId(), effectiveUserId, "SUBMIT_FOR_REVIEW", ApprovalStatus.DRAFT, ApprovalStatus.PENDING, "Gửi duyệt ngay khi tạo");
        }

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO submitQuestionForReview(UUID questionId, UUID currentUserId, String role, String comment) {
        User resolvedUser = resolveCurrentUser(currentUserId);
        UUID effectiveUserId = resolvedUser != null ? resolvedUser.getUserId() : currentUserId;
        String effectiveRole = resolveRole(resolvedUser, role);

        QuestionBank question = questionBankRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(effectiveRole) && !Objects.equals(question.getCreatedBy(), effectiveUserId)) {
            throw new CustomException("Chỉ người tạo hoặc quản trị viên mới có quyền gửi câu hỏi đi duyệt", HttpStatus.FORBIDDEN);
        }

        ApprovalStatus fromStatus = question.getApprovalStatus();
        if (fromStatus == ApprovalStatus.PENDING) {
            throw new CustomException("Câu hỏi đã ở trạng thái chờ duyệt (PENDING)", HttpStatus.CONFLICT);
        }
        if (fromStatus == ApprovalStatus.APPROVED) {
            throw new CustomException("Câu hỏi đã được phê duyệt, không thể gửi duyệt lại", HttpStatus.BAD_REQUEST);
        }

        question.setApprovalStatus(ApprovalStatus.PENDING);
        question.setReviewedBy(null);
        question.setReviewedAt(null);
        question.setRejectionReason(null);

        question = questionBankRepository.save(question);

        recordApprovalAudit(questionId, effectiveUserId, "SUBMIT_FOR_REVIEW", fromStatus, ApprovalStatus.PENDING,
                comment != null && !comment.trim().isEmpty() ? comment.trim() : "Gửi câu hỏi xét duyệt");

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO approveQuestion(UUID questionId) {
        return approveQuestion(questionId, null, null, null);
    }

    @Override
    @Transactional
    public QuestionBankDTO approveQuestion(UUID questionId, UUID reviewerId, String role, String comment) {
        User resolvedUser = resolveCurrentUser(reviewerId);
        UUID effectiveReviewerId = resolvedUser != null ? resolvedUser.getUserId() : reviewerId;
        String effectiveRole = resolveRole(resolvedUser, role);

        if (!"ADMIN".equalsIgnoreCase(effectiveRole)) {
            throw new CustomException("Chỉ quản trị viên (ADMIN) mới có quyền phê duyệt câu hỏi", HttpStatus.FORBIDDEN);
        }

        QuestionBank question = questionBankRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        ApprovalStatus currentStatus = question.getApprovalStatus();
        if (currentStatus == ApprovalStatus.APPROVED) {
            throw new CustomException("Câu hỏi đã được phê duyệt trước đó", HttpStatus.CONFLICT);
        }
        if (currentStatus == ApprovalStatus.DRAFT) {
            throw new CustomException("Câu hỏi ở trạng thái DRAFT phải được gửi duyệt trước khi phê duyệt", HttpStatus.BAD_REQUEST);
        }
        if (currentStatus == ApprovalStatus.REJECTED) {
            throw new CustomException("Câu hỏi đã bị từ chối. Cần sửa và gửi duyệt lại trước khi phê duyệt", HttpStatus.BAD_REQUEST);
        }
        if (currentStatus != ApprovalStatus.PENDING) {
            throw new CustomException("Câu hỏi không ở trạng thái PENDING để phê duyệt", HttpStatus.BAD_REQUEST);
        }

        question.setApprovalStatus(ApprovalStatus.APPROVED);
        question.setReviewedBy(effectiveReviewerId);
        question.setReviewedAt(Instant.now());
        question.setRejectionReason(null);

        question = questionBankRepository.save(question);

        recordApprovalAudit(questionId, effectiveReviewerId, "APPROVE", ApprovalStatus.PENDING, ApprovalStatus.APPROVED,
                comment != null && !comment.trim().isEmpty() ? comment.trim() : "Phê duyệt câu hỏi vào ngân hàng");

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO rejectQuestion(UUID questionId, String rejectionReason, UUID reviewerId, String role, String comment) {
        User resolvedUser = resolveCurrentUser(reviewerId);
        UUID effectiveReviewerId = resolvedUser != null ? resolvedUser.getUserId() : reviewerId;
        String effectiveRole = resolveRole(resolvedUser, role);

        if (!"ADMIN".equalsIgnoreCase(effectiveRole)) {
            throw new CustomException("Chỉ quản trị viên (ADMIN) mới có quyền từ chối câu hỏi", HttpStatus.FORBIDDEN);
        }

        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new CustomException("Lý do từ chối không được để trống", HttpStatus.BAD_REQUEST);
        }

        QuestionBank question = questionBankRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        ApprovalStatus currentStatus = question.getApprovalStatus();
        if (currentStatus == ApprovalStatus.REJECTED) {
            throw new CustomException("Câu hỏi đã bị từ chối trước đó", HttpStatus.CONFLICT);
        }
        if (currentStatus == ApprovalStatus.DRAFT) {
            throw new CustomException("Câu hỏi ở trạng thái DRAFT chưa được gửi duyệt", HttpStatus.BAD_REQUEST);
        }
        if (currentStatus == ApprovalStatus.APPROVED) {
            throw new CustomException("Câu hỏi đã được phê duyệt, không thể từ chối trực tiếp", HttpStatus.BAD_REQUEST);
        }
        if (currentStatus != ApprovalStatus.PENDING) {
            throw new CustomException("Câu hỏi không ở trạng thái PENDING để từ chối", HttpStatus.BAD_REQUEST);
        }

        String cleanReason = rejectionReason.trim();
        question.setApprovalStatus(ApprovalStatus.REJECTED);
        question.setReviewedBy(effectiveReviewerId);
        question.setReviewedAt(Instant.now());
        question.setRejectionReason(cleanReason);

        question = questionBankRepository.save(question);

        String auditComment = cleanReason;
        if (comment != null && !comment.trim().isEmpty()) {
            auditComment += " - " + comment.trim();
        }
        recordApprovalAudit(questionId, effectiveReviewerId, "REJECT", ApprovalStatus.PENDING, ApprovalStatus.REJECTED, auditComment);

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO updateQuestion(UUID questionId, CreateQuestionDTO dto, UUID currentUserId, String role) {
        User resolvedUser = resolveCurrentUser(currentUserId);
        UUID effectiveUserId = resolvedUser != null ? resolvedUser.getUserId() : currentUserId;
        String effectiveRole = resolveRole(resolvedUser, role);

        QuestionBank question = questionBankRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(effectiveRole) && !Objects.equals(question.getCreatedBy(), effectiveUserId)) {
            throw new CustomException("You do not have permission to update this question", HttpStatus.FORBIDDEN);
        }

        checkSubjectPermission(dto.getSubjectId(), effectiveUserId, effectiveRole);

        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        Topic topic = topicRepository.findById(dto.getTopicId())
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(topic.getSubjectId(), dto.getSubjectId())) {
            throw new CustomException("Topic does not belong to the selected subject", HttpStatus.BAD_REQUEST);
        }

        if (dto.getQuestionType() != QuestionType.MCQ_SINGLE && dto.getQuestionType() != QuestionType.FILL_BLANK) {
            throw new CustomException("Hệ thống chỉ hỗ trợ 2 dạng câu hỏi: Trắc nghiệm (MCQ_SINGLE) và Điền đáp án (FILL_BLANK). Loại '" + dto.getQuestionType() + "' không được hỗ trợ", HttpStatus.BAD_REQUEST);
        }

        if (dto.getQuestionType() == QuestionType.FILL_BLANK) {
            List<String> cleanAnswers = extractAcceptedAnswers(dto);
            if (cleanAnswers.isEmpty()) {
                throw new CustomException("Câu hỏi điền đáp án (FILL_BLANK) phải có ít nhất 1 đáp án được chấp nhận", HttpStatus.BAD_REQUEST);
            }
        }

        if (dto.getQuestionType() == QuestionType.MCQ_SINGLE && dto.getOptions() != null) {
            long correctCount = dto.getOptions().stream()
                    .filter(opt -> Boolean.TRUE.equals(opt.getIsCorrect()))
                    .count();
            if (correctCount != 1) {
                throw new CustomException("Câu hỏi trắc nghiệm một đáp án đúng (MCQ_SINGLE) phải có chính xác 1 đáp án đúng", HttpStatus.BAD_REQUEST);
            }
        }

        boolean isUsedInExam = examQuestionRepository.existsByQuestionId(questionId);
        ApprovalStatus fromStatus = question.getApprovalStatus();
        ApprovalStatus nextStatus;

        if ("ADMIN".equals(effectiveRole) && dto.getApprovalStatus() == ApprovalStatus.APPROVED) {
            nextStatus = ApprovalStatus.APPROVED;
        } else if (fromStatus == ApprovalStatus.REJECTED) {
            nextStatus = (dto.getApprovalStatus() == ApprovalStatus.PENDING) ? ApprovalStatus.PENDING : ApprovalStatus.DRAFT;
        } else if (fromStatus == ApprovalStatus.DRAFT) {
            nextStatus = (dto.getApprovalStatus() == ApprovalStatus.PENDING) ? ApprovalStatus.PENDING : ApprovalStatus.DRAFT;
        } else if (fromStatus == ApprovalStatus.PENDING) {
            nextStatus = ApprovalStatus.PENDING;
        } else { // APPROVED -> edit requires re-approval unless already used in an exam (to keep active exams operational)
            if (isUsedInExam) {
                nextStatus = ApprovalStatus.APPROVED;
            } else {
                nextStatus = (dto.getApprovalStatus() == ApprovalStatus.PENDING) ? ApprovalStatus.PENDING : ApprovalStatus.DRAFT;
            }
        }

        question.setSubjectId(dto.getSubjectId());
        question.setTopicId(dto.getTopicId());
        question.setQuestionType(dto.getQuestionType());
        question.setContent(dto.getContent());
        question.setMediaUrl(dto.getMediaUrl());
        question.setDifficultyLevel(dto.getDifficultyLevel());
        question.setCognitiveLevel(dto.getCognitiveLevel());
        question.setApprovalStatus(nextStatus);

        if (nextStatus != ApprovalStatus.REJECTED) {
            question.setRejectionReason(null);
        }

        question = questionBankRepository.save(question);

        if (dto.getQuestionType() == QuestionType.FILL_BLANK) {
            questionOptionRepository.deleteByQuestionId(questionId);
            List<String> cleanAnswers = extractAcceptedAnswers(dto);
            List<QuestionAcceptedAnswer> existingAnswers = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
            int minAns = Math.min(existingAnswers.size(), cleanAnswers.size());
            for (int i = 0; i < minAns; i++) {
                QuestionAcceptedAnswer existing = existingAnswers.get(i);
                existing.setAcceptedText(cleanAnswers.get(i));
                existing.setOrderIndex(i);
                questionAcceptedAnswerRepository.save(existing);
            }
            if (cleanAnswers.size() > existingAnswers.size()) {
                for (int i = existingAnswers.size(); i < cleanAnswers.size(); i++) {
                    QuestionAcceptedAnswer accepted = QuestionAcceptedAnswer.builder()
                            .questionId(question.getQuestionId())
                            .acceptedText(cleanAnswers.get(i))
                            .orderIndex(i)
                            .build();
                    questionAcceptedAnswerRepository.save(accepted);
                }
            } else if (existingAnswers.size() > cleanAnswers.size()) {
                for (int i = cleanAnswers.size(); i < existingAnswers.size(); i++) {
                    questionAcceptedAnswerRepository.delete(existingAnswers.get(i));
                }
            }
        } else if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            questionAcceptedAnswerRepository.deleteByQuestionId(questionId);
            List<CreateQuestionDTO.CreateQuestionOptionDTO> newOpts = dto.getOptions();
            List<QuestionOption> existingOptions = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
            int minOpts = Math.min(existingOptions.size(), newOpts.size());
            for (int i = 0; i < minOpts; i++) {
                QuestionOption opt = existingOptions.get(i);
                CreateQuestionDTO.CreateQuestionOptionDTO optDto = newOpts.get(i);
                opt.setOptionText(optDto.getContent());
                opt.setIsCorrect(optDto.getIsCorrect());
                opt.setOrderIndex(optDto.getOrderIndex() != null ? optDto.getOrderIndex() : i);
                questionOptionRepository.save(opt);
            }
            if (newOpts.size() > existingOptions.size()) {
                for (int i = existingOptions.size(); i < newOpts.size(); i++) {
                    CreateQuestionDTO.CreateQuestionOptionDTO optDto = newOpts.get(i);
                    QuestionOption option = QuestionOption.builder()
                            .questionId(question.getQuestionId())
                            .optionText(optDto.getContent())
                            .isCorrect(optDto.getIsCorrect())
                            .orderIndex(optDto.getOrderIndex() != null ? optDto.getOrderIndex() : i)
                            .build();
                    questionOptionRepository.save(option);
                }
            } else if (existingOptions.size() > newOpts.size()) {
                for (int i = newOpts.size(); i < existingOptions.size(); i++) {
                    questionOptionRepository.delete(existingOptions.get(i));
                }
            }
        } else {
            questionOptionRepository.deleteByQuestionId(questionId);
            questionAcceptedAnswerRepository.deleteByQuestionId(questionId);
        }

        if (dto.getCloIds() != null) {
            syncQuestionClos(question, dto.getCloIds());
        }

        recordApprovalAudit(questionId, effectiveUserId, "UPDATE_QUESTION", fromStatus, nextStatus, "Cập nhật nội dung câu hỏi");

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public QuestionBankDTO assignClosToQuestion(UUID questionId, List<UUID> cloIds, UUID currentUserId, String role) {
        User resolvedUser = resolveCurrentUser(currentUserId);
        UUID effectiveUserId = resolvedUser != null ? resolvedUser.getUserId() : currentUserId;
        String effectiveRole = resolveRole(resolvedUser, role);

        QuestionBank question = questionBankRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(effectiveRole) && !Objects.equals(question.getCreatedBy(), effectiveUserId)) {
            throw new CustomException("You do not have permission to update CLOs for this question", HttpStatus.FORBIDDEN);
        }

        syncQuestionClos(question, cloIds != null ? cloIds : new ArrayList<>());

        return mapToDTO(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(UUID questionId, UUID currentUserId, String role) {
        User resolvedUser = resolveCurrentUser(currentUserId);
        UUID effectiveUserId = resolvedUser != null ? resolvedUser.getUserId() : currentUserId;
        String effectiveRole = resolveRole(resolvedUser, role);

        QuestionBank question = questionBankRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(effectiveRole) && !Objects.equals(question.getCreatedBy(), effectiveUserId)) {
            throw new CustomException("You do not have permission to delete this question", HttpStatus.FORBIDDEN);
        }

        if (examQuestionRepository.existsByQuestionId(questionId) || examAnswerRepository.existsByQuestionId(questionId)
                || practiceSessionQuestionRepository.existsByQuestionId(questionId)) {
            throw new CustomException("Cannot delete question that has already been used in an exam or practice session", HttpStatus.BAD_REQUEST);
        }

        if (questionApprovalRepository.countByQuestionId(questionId) > 0) {
            throw new CustomException("Không thể xóa câu hỏi đã có biên bản xét duyệt. Vui lòng giữ lại để đảm bảo tính toàn vẹn kiểm toán", HttpStatus.CONFLICT);
        }

        questionCloRepository.deleteByQuestionId(questionId);
        questionOptionRepository.deleteByQuestionId(questionId);
        questionAcceptedAnswerRepository.deleteByQuestionId(questionId);
        questionBankRepository.delete(question);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionApprovalDTO> getQuestionApprovalHistory(UUID questionId, UUID currentUserId, String role) {
        if (!questionBankRepository.existsById(questionId)) {
            throw new CustomException("Question not found", HttpStatus.NOT_FOUND);
        }

        List<QuestionApproval> history = questionApprovalRepository.findByQuestionIdOrderByCreatedAtDesc(questionId);
        return history.stream().map(a -> {
            String actorName = null;
            if (a.getActorId() != null) {
                actorName = userRepository.findById(a.getActorId())
                        .map(User::getUsername)
                        .orElse(null);
            }
            return QuestionApprovalDTO.builder()
                    .approvalId(a.getApprovalId())
                    .questionId(a.getQuestionId())
                    .actorId(a.getActorId())
                    .actorName(actorName)
                    .action(a.getAction())
                    .fromStatus(a.getFromStatus())
                    .toStatus(a.getToStatus())
                    .comment(a.getComment())
                    .createdAt(a.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
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

    private void syncQuestionClos(QuestionBank question, List<UUID> cloIds) {
        List<UUID> distinctCloIds = cloIds.stream().distinct().toList();

        for (UUID cloId : distinctCloIds) {
            Clo clo = cloRepository.findById(cloId)
                    .orElseThrow(() -> new CustomException("Chuẩn đầu ra CLO (ID: " + cloId + ") không tồn tại", HttpStatus.NOT_FOUND));

            if (!Objects.equals(clo.getSubjectId(), question.getSubjectId())) {
                throw new CustomException("CLO '" + clo.getCloCode() + "' không thuộc cùng môn học với câu hỏi", HttpStatus.BAD_REQUEST);
            }

            if (!Boolean.TRUE.equals(clo.getIsActive())) {
                throw new CustomException("CLO '" + clo.getCloCode() + "' đã bị ngưng hoạt động, không thể gán cho câu hỏi", HttpStatus.BAD_REQUEST);
            }
        }

        questionCloRepository.deleteByQuestionId(question.getQuestionId());

        for (UUID cloId : distinctCloIds) {
            QuestionClo link = QuestionClo.builder()
                    .questionId(question.getQuestionId())
                    .cloId(cloId)
                    .subjectId(question.getSubjectId())
                    .build();
            questionCloRepository.save(link);
        }
    }

    private void recordApprovalAudit(UUID questionId, UUID actorId, String action, ApprovalStatus fromStatus, ApprovalStatus toStatus, String comment) {
        QuestionApproval audit = QuestionApproval.builder()
                .questionId(questionId)
                .actorId(actorId)
                .action(action)
                .fromStatus(fromStatus != null ? fromStatus.name() : null)
                .toStatus(toStatus != null ? toStatus.name() : null)
                .comment(comment)
                .build();
        questionApprovalRepository.save(audit);
    }

    private void checkSubjectPermission(UUID subjectId, UUID currentUserId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            throw new CustomException("Sinh viên không có quyền truy cập ngân hàng câu hỏi", HttpStatus.FORBIDDEN);
        }
        boolean isAssigned = classRepository.isInstructorAssignedToSubject(subjectId, currentUserId);
        if (!isAssigned) {
            throw new CustomException("Giảng viên chưa được phân công giảng dạy môn học này", HttpStatus.FORBIDDEN);
        }
    }

    private User resolveCurrentUser(UUID explicitUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User user = userRepository.findByUsername(auth.getName());
            if (user != null) {
                return user;
            }
        }
        if (explicitUserId != null) {
            return userRepository.findById(explicitUserId).orElse(null);
        }
        return null;
    }

    private String resolveRole(User user, String explicitRole) {
        if (user != null && user.getRole() != null) {
            return user.getRole().name();
        }
        if (explicitRole != null && !explicitRole.trim().isEmpty()) {
            return explicitRole.replace("ROLE_", "").toUpperCase();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            for (var a : auth.getAuthorities()) {
                if (a.getAuthority().contains("ADMIN")) return "ADMIN";
                if (a.getAuthority().contains("INSTRUCTOR")) return "INSTRUCTOR";
                if (a.getAuthority().contains("STUDENT")) return "STUDENT";
            }
        }
        return "INSTRUCTOR";
    }

    private List<String> extractAcceptedAnswers(CreateQuestionDTO dto) {
        List<String> rawAnswers = new ArrayList<>();
        if (dto.getAcceptedAnswers() != null) {
            rawAnswers.addAll(dto.getAcceptedAnswers());
        }
        return rawAnswers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private QuestionBankDTO mapToDTO(QuestionBank question) {
        Topic topic = (question.getTopicId() != null)
                ? topicRepository.findById(question.getTopicId()).orElse(null)
                : null;
        UUID chapterId = (topic != null) ? topic.getChapterId() : null;

        List<QuestionOptionDTO> optionDTOs;
        List<String> acceptedAnswersList;

        if (question.getQuestionType() == QuestionType.FILL_BLANK) {
            optionDTOs = Collections.emptyList();
            List<QuestionAcceptedAnswer> acceptedAnswers =
                    questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(question.getQuestionId());
            acceptedAnswersList = acceptedAnswers.stream()
                    .map(QuestionAcceptedAnswer::getAcceptedText)
                    .collect(Collectors.toList());
        } else {
            List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByOrderIndexAsc(question.getQuestionId());
            optionDTOs = options.stream().map(opt -> QuestionOptionDTO.builder()
                    .optionId(opt.getOptionId())
                    .questionId(opt.getQuestionId())
                    .content(opt.getOptionText())
                    .isCorrect(opt.getIsCorrect())
                    .orderIndex(opt.getOrderIndex())
                    .build()).collect(Collectors.toList());
            acceptedAnswersList = Collections.emptyList();
        }

        List<UUID> cloIds = questionCloRepository.findByQuestionId(question.getQuestionId()).stream()
                .map(QuestionClo::getCloId)
                .collect(Collectors.toList());

        return QuestionBankDTO.builder()
                .questionId(question.getQuestionId())
                .subjectId(question.getSubjectId())
                .chapterId(chapterId)
                .topicId(question.getTopicId())
                .questionType(question.getQuestionType())
                .content(question.getContent())
                .mediaUrl(question.getMediaUrl())
                .difficultyLevel(question.getDifficultyLevel())
                .cognitiveLevel(question.getCognitiveLevel())
                .approvalStatus(question.getApprovalStatus())
                .createdBy(question.getCreatedBy())
                .createdAt(question.getCreatedAt())
                .reviewedBy(question.getReviewedBy())
                .reviewedAt(question.getReviewedAt())
                .rejectionReason(question.getRejectionReason())
                .cloIds(cloIds)
                .options(optionDTOs)
                .acceptedAnswers(acceptedAnswersList)
                .build();
    }
}