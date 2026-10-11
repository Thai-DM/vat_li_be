package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.CreateTopicDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.ITopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements ITopicService {

    private final TopicRepository topicRepository;
    private final ISubjectRepository subjectRepository;
    private final IChapterRepository chapterRepository;
    private final IClassRepository classRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TopicDTO> getTopicsBySubject(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        return topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDTO> getAllTopics() {
        return topicRepository.findAllByOrderByOrderIndexAsc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TopicDTO getTopicById(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));
        return mapToDTO(topic);
    }

    @Override
    @Transactional
    public TopicDTO createTopic(CreateTopicDTO dto) {
        checkSubjectManagePermission(dto.getSubjectId());

        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        if (topicRepository.existsBySubjectIdAndTopicName(dto.getSubjectId(), dto.getTopicName())) {
            throw new CustomException("Topic name already exists in this subject", HttpStatus.BAD_REQUEST);
        }

        if (dto.getChapterId() != null) {
            Chapter chapter = chapterRepository.findById(dto.getChapterId())
                    .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));
            if (!chapter.getSubjectId().equals(dto.getSubjectId())) {
                throw new CustomException("Chương không thuộc môn học này", HttpStatus.BAD_REQUEST);
            }
            if (!Boolean.TRUE.equals(chapter.getIsActive())) {
                throw new CustomException("Không thể gán bài học vào chương đã ngừng hoạt động", HttpStatus.BAD_REQUEST);
            }
        }

        Topic topic = Topic.builder()
                .subjectId(dto.getSubjectId())
                .chapterId(dto.getChapterId())
                .topicName(dto.getTopicName())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .description(dto.getDescription())
                .build();

        topic = topicRepository.save(topic);
        return mapToDTO(topic);
    }

    @Override
    @Transactional
    public TopicDTO updateTopic(UUID topicId, CreateTopicDTO dto) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(topic.getSubjectId());

        if (!topic.getSubjectId().equals(dto.getSubjectId())) {
            throw new CustomException("Cannot change subject of a topic", HttpStatus.BAD_REQUEST);
        }

        if (!topic.getTopicName().equals(dto.getTopicName()) &&
                topicRepository.existsBySubjectIdAndTopicName(dto.getSubjectId(), dto.getTopicName())) {
            throw new CustomException("Topic name already exists in this subject", HttpStatus.BAD_REQUEST);
        }

        // Backward compatibility: If request omits chapterId (null), KEEP existing chapterId
        if (dto.getChapterId() != null) {
            Chapter chapter = chapterRepository.findById(dto.getChapterId())
                    .orElseThrow(() -> new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND));
            if (!chapter.getSubjectId().equals(topic.getSubjectId())) {
                throw new CustomException("Chương không thuộc cùng môn học với bài học", HttpStatus.BAD_REQUEST);
            }
            if (!Boolean.TRUE.equals(chapter.getIsActive()) && !chapter.getChapterId().equals(topic.getChapterId())) {
                throw new CustomException("Không thể chuyển bài học vào chương đã ngừng hoạt động", HttpStatus.BAD_REQUEST);
            }
            topic.setChapterId(dto.getChapterId());
        }

        topic.setTopicName(dto.getTopicName());
        topic.setOrderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0);
        topic.setDescription(dto.getDescription());

        topic = topicRepository.save(topic);
        return mapToDTO(topic);
    }

    @Override
    @Transactional
    public void deleteTopic(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(topic.getSubjectId());
        topicRepository.delete(topic);
    }

    @Override
    @Transactional
    public TopicDTO assignChapter(UUID topicId, UUID chapterId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Bài học không tồn tại", HttpStatus.NOT_FOUND));

        checkSubjectManagePermission(topic.getSubjectId());

        if (chapterId != null) {
            Chapter chapter = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new CustomException("Chương đích không tồn tại", HttpStatus.NOT_FOUND));

            if (!chapter.getSubjectId().equals(topic.getSubjectId())) {
                throw new CustomException("Chương đích không thuộc cùng môn học với bài học", HttpStatus.BAD_REQUEST);
            }

            if (!Boolean.TRUE.equals(chapter.getIsActive())) {
                throw new CustomException("Không thể chuyển bài học vào chương đã ngừng hoạt động", HttpStatus.BAD_REQUEST);
            }

            topic.setChapterId(chapterId);
        } else {
            // Proactive unassign
            topic.setChapterId(null);
        }

        topic = topicRepository.save(topic);
        return mapToDTO(topic);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDTO> getTopicsByChapter(UUID chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new CustomException("Chương không tồn tại", HttpStatus.NOT_FOUND);
        }
        return topicRepository.findByChapterIdOrderByOrderIndexAsc(chapterId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDTO> getUnassignedTopicsBySubject(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Môn học không tồn tại", HttpStatus.NOT_FOUND);
        }
        return topicRepository.findBySubjectIdAndChapterIdIsNullOrderByOrderIndexAsc(subjectId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private void checkSubjectManagePermission(UUID subjectId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return;
        }

        boolean isInstructor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INSTRUCTOR".equals(a.getAuthority()) || "INSTRUCTOR".equals(a.getAuthority()));
        if (isInstructor) {
            User user = userRepository.findByUsername(auth.getName());
            if (user != null && classRepository.isInstructorAssignedToSubject(subjectId, user.getUserId())) {
                return;
            }
            throw new CustomException("Giảng viên chưa được phân công giảng dạy môn học này", HttpStatus.FORBIDDEN);
        }

        throw new CustomException("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
    }

    private TopicDTO mapToDTO(Topic topic) {
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