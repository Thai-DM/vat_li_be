package com.vatly1.example.service;

import com.vatly1.example.dto.request.CreateTopicDTO;
import com.vatly1.example.dto.TopicDTO;

import java.util.List;
import java.util.UUID;

public interface ITopicService {
    List<TopicDTO> getTopicsBySubject(UUID subjectId);
    TopicDTO getTopicById(UUID topicId);
    TopicDTO createTopic(CreateTopicDTO dto);
    TopicDTO updateTopic(UUID topicId, CreateTopicDTO dto);
    void deleteTopic(UUID topicId);
}
