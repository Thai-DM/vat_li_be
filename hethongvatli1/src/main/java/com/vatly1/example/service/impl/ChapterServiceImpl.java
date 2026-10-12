package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.ReorderTopicsDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements IChapterService {

    private final IChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final ISubjectRepository subjectRepository;
    private final IClassRepository classRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional
    public ChapterDTO createChapter(UUID subjectId, CreateChapterDTO dto) {
        checkSubjectManagePermission(subjectId);

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new CustomException("Môn học không tồn tại", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(subject.getIsActive())) {
            throw new CustomException("Không thể tạo chương cho môn học đã ngừng hoạt động", HttpStatus.BAD_REQUEST);
        }

        if (dto.getChapterCode() == null || dto.getChapterCode().trim().isEmpty()) {
            throw new CustomException("Mã chương không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (dto.getChapterName() == null || dto.getChapterName().trim().isEmpty()) {
            throw new CustomException("Tên chương không được để trống", HttpStatus.BAD_REQUEST);
        }

        String cleanCode = dto.getChapterCode().trim();
        if (chapterRepository.existsBySubjectIdAndChapterCode(subjectId, cleanCode)) {
            throw new CustomException("Mã chương '" + cleanCode + "' đã tồn tại trong môn học này", HttpStatus.CONFLICT);
        }

        Chapter chapter = Chapter.builder()
                .subjectId(subjectId)
                .chapterCode(cleanCode)
                .chapterName(dto.getChapterName().trim())
                .description(dto.getDescription())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        Chapter saved = chapterRepository.save(chapter);
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChapterDTO> getChaptersBySubject(UUID subjectId, Boolean activeOnly) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Môn học không tồn tại", HttpStatus.NOT_FOUND);
        }

        List<Chapter> chapters;
        if (Boolean.TRUE.equals(activeOnly)) {
            chapters = chapterRepository.findBySubjectIdAndIsActiveTrueOrderByOrderIndexAsc(subjectId);
        } else {
            chapters = chapterRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId);
        }

        return chapters.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterDTO getChapterById(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));
        return mapToDTO(chapter);
    }

    @Override
    @Transactional
    public ChapterDTO updateChapter(UUID chapterId, UpdateChapterDTO dto) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(chapter.getSubjectId());

        if (dto.getChapterCode() != null) {
            String cleanCode = dto.getChapterCode().trim();
            if (cleanCode.isEmpty()) {
                throw new CustomException("Mã chương không được để trống", HttpStatus.BAD_REQUEST);
            }
            if (chapterRepository.existsBySubjectIdAndChapterCodeAndChapterIdNot(chapter.getSubjectId(), cleanCode, chapterId)) {
                throw new CustomException("Mã chương '" + cleanCode + "' đã tồn tại trong môn học này", HttpStatus.CONFLICT);
            }
            chapter.setChapterCode(cleanCode);
        }

        if (dto.getChapterName() != null) {
            String cleanName = dto.getChapterName().trim();
            if (cleanName.isEmpty()) {
                throw new CustomException("Tên chương không được để trống", HttpStatus.BAD_REQUEST);
            }
            chapter.setChapterName(cleanName);
        }

        if (dto.getDescription() != null) {
            chapter.setDescription(dto.getDescription());
        }

        if (dto.getOrderIndex() != null) {
            chapter.setOrderIndex(dto.getOrderIndex());
        }

        if (dto.getIsActive() != null) {
            chapter.setIsActive(dto.getIsActive());
        }

        Chapter updated = chapterRepository.save(chapter);
        return mapToDTO(updated);
    }

    @Override
    @Transactional
    public ChapterDTO updateChapterStatus(UUID chapterId, Boolean isActive) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(chapter.getSubjectId());

        if (isActive == null) {
            throw new CustomException("Trạng thái hoạt động không được để trống", HttpStatus.BAD_REQUEST);
        }

        chapter.setIsActive(isActive);
        Chapter updated = chapterRepository.save(chapter);
        return mapToDTO(updated);
    }

    @Override
    @Transactional
    public void deleteChapter(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(chapter.getSubjectId());

        long count = topicRepository.countByChapterId(chapterId);
        if (count > 0) {
            throw new CustomException("Không thể xóa chương đang chứa " + count + " bài học/chủ đề. Vui lòng chuyển hoặc gỡ bài học trước khi xóa.", HttpStatus.BAD_REQUEST);
        }

        chapterRepository.delete(chapter);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTopicCount(UUID chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND);
        }
        return topicRepository.countByChapterId(chapterId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDTO> getTopicsByChapter(UUID chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND);
        }
        return topicRepository.findByChapterIdOrderByOrderIndexAsc(chapterId).stream()
                .map(this::mapTopicToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TopicDTO> reorderTopics(UUID chapterId, ReorderTopicsDTO dto) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(chapter.getSubjectId());

        if (dto.getTopicIds() == null || dto.getTopicIds().isEmpty()) {
            throw new CustomException("Danh sách ID bài học không được để trống", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> uniqueIds = new HashSet<>(dto.getTopicIds());
        if (uniqueIds.size() != dto.getTopicIds().size()) {
            throw new CustomException("Danh sách ID bài học bị trùng lặp", HttpStatus.BAD_REQUEST);
        }

        List<Topic> currentTopics = topicRepository.findByChapterIdOrderByOrderIndexAsc(chapterId);
        if (dto.getTopicIds().size() != currentTopics.size()) {
            throw new CustomException("Danh sách bài học sắp xếp lại phải chứa đầy đủ tất cả bài học trong chương (yêu cầu " + currentTopics.size() + " bài học, nhận " + dto.getTopicIds().size() + ")", HttpStatus.BAD_REQUEST);
        }

        Map<UUID, Topic> topicMap = currentTopics.stream().collect(Collectors.toMap(Topic::getTopicId, t -> t));
        for (UUID id : dto.getTopicIds()) {
            if (!topicMap.containsKey(id)) {
                throw new CustomException("Tồn tại bài học không thuộc chương này: " + id, HttpStatus.BAD_REQUEST);
            }
        }

        List<Topic> updatedList = new ArrayList<>();
        for (int i = 0; i < dto.getTopicIds().size(); i++) {
            Topic topic = topicMap.get(dto.getTopicIds().get(i));
            topic.setOrderIndex(i + 1);
            updatedList.add(topic);
        }

        topicRepository.saveAll(updatedList);
        return updatedList.stream().map(this::mapTopicToDTO).collect(Collectors.toList());
    }

    private void checkSubjectManagePermission(UUID subjectId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomException("Vui lòng đăng nhập để thực hiện thao tác này", HttpStatus.UNAUTHORIZED);
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new CustomException("Chỉ Quản trị viên (ADMIN) mới có quyền quản lý chương học", HttpStatus.FORBIDDEN);
        }
    }

    private ChapterDTO mapToDTO(Chapter chapter) {
        long topicCount = topicRepository.countByChapterId(chapter.getChapterId());
        return ChapterDTO.builder()
                .chapterId(chapter.getChapterId())
                .subjectId(chapter.getSubjectId())
                .chapterCode(chapter.getChapterCode())
                .chapterName(chapter.getChapterName())
                .description(chapter.getDescription())
                .orderIndex(chapter.getOrderIndex())
                .isActive(chapter.getIsActive())
                .topicCount(topicCount)
                .createdAt(chapter.getCreatedAt())
                .updatedAt(chapter.getUpdatedAt())
                .build();
    }

    private TopicDTO mapTopicToDTO(Topic topic) {
        return TopicDTO.builder()
                .topicId(topic.getTopicId())
                .subjectId(topic.getSubjectId())
                .chapterId(topic.getChapterId())
                .topicName(topic.getTopicName())
                .orderIndex(topic.getOrderIndex())
                .description(topic.getDescription())
                .build();
    }
}
