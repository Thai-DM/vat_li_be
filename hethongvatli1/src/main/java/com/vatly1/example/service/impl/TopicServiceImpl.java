package com.vatly1.example.service.impl;

import com.vatly1.example.dto.request.CreateTopicDTO;
import com.vatly1.example.dto.TopicDTO;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.ITopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @Override
    public List<TopicDTO> getTopicsBySubject(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        return topicRepository.findBySubjectIdOrderByOrderIndexAsc(subjectId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TopicDTO getTopicById(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException("Topic not found", HttpStatus.NOT_FOUND));
        return mapToDTO(topic);
    }

    @Override
    @Transactional
    public TopicDTO createTopic(CreateTopicDTO dto) {
        if (!subjectRepository.existsById(dto.getSubjectId())) {
            throw new CustomException("Subject not found", HttpStatus.NOT_FOUND);
        }
        if (topicRepository.existsBySubjectIdAndTopicName(dto.getSubjectId(), dto.getTopicName())) {
            throw new CustomException("Topic name already exists in this subject", HttpStatus.BAD_REQUEST);
        }

        Topic topic = Topic.builder()
                .subjectId(dto.getSubjectId())
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

        if (!topic.getSubjectId().equals(dto.getSubjectId())) {
            throw new CustomException("Cannot change subject of a topic", HttpStatus.BAD_REQUEST);
        }

        if (!topic.getTopicName().equals(dto.getTopicName()) &&
                topicRepository.existsBySubjectIdAndTopicName(dto.getSubjectId(), dto.getTopicName())) {
            throw new CustomException("Topic name already exists in this subject", HttpStatus.BAD_REQUEST);
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
        topicRepository.delete(topic);
    }

    private TopicDTO mapToDTO(Topic topic) {
        return TopicDTO.builder()
                .topicId(topic.getTopicId())
                .subjectId(topic.getSubjectId())
                .topicName(topic.getTopicName())
                .orderIndex(topic.getOrderIndex())
                .description(topic.getDescription())
                .build();
    }
}
