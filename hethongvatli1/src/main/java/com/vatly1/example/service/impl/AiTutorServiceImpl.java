package com.vatly1.example.service.impl;

import com.vatly1.example.dto.dto.AiConversationDTO;
import com.vatly1.example.dto.dto.AiFeedbackDTO;
import com.vatly1.example.dto.dto.AiMessageDTO;
import com.vatly1.example.dto.request.SendAiMessageDTO;
import com.vatly1.example.dto.request.StartAiConversationDTO;
import com.vatly1.example.entity.AiConversation;
import com.vatly1.example.entity.AiFeedback;
import com.vatly1.example.entity.AiMessage;
import com.vatly1.example.entity.enums.AiMode;
import com.vatly1.example.entity.enums.AiSender;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.IAiConversationRepository;
import com.vatly1.example.repository.IAiFeedbackRepository;
import com.vatly1.example.repository.IAiMessageRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IAiTutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTutorServiceImpl implements IAiTutorService {

    private final IAiConversationRepository conversationRepository;
    private final IAiMessageRepository messageRepository;
    private final IAiFeedbackRepository feedbackRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final TopicRepository topicRepository;

    @Override
    @Transactional
    public AiConversationDTO startConversation(StartAiConversationDTO dto, UUID studentId) {
        if (!classRepository.existsById(dto.getClassId())) {
            throw new CustomException("Class not found", HttpStatus.NOT_FOUND);
        }

        if (!classEnrollmentRepository.existsByClassIdAndStudentId(dto.getClassId(), studentId)) {
            throw new CustomException("Access denied: You are not enrolled in this class", HttpStatus.FORBIDDEN);
        }

        if (dto.getTopicId() != null && !topicRepository.existsById(dto.getTopicId())) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }

        AiConversation conversation = AiConversation.builder()
                .studentId(studentId)
                .classId(dto.getClassId())
                .topicId(dto.getTopicId())
                .mode(dto.getMode() != null ? dto.getMode() : AiMode.TEXT)
                .startedAt(Instant.now())
                .build();

        AiConversation saved = conversationRepository.save(conversation);
        return toConversationDTO(saved);
    }

    @Override
    @Transactional
    public AiMessageDTO sendMessage(UUID conversationId, SendAiMessageDTO dto, UUID studentId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(conversation.getStudentId(), studentId)) {
            throw new CustomException("Access denied: You cannot send messages in another student's conversation", HttpStatus.FORBIDDEN);
        }

        if (conversation.getEndedAt() != null) {
            throw new CustomException("Cannot send message: Conversation has already ended", HttpStatus.BAD_REQUEST);
        }

        AiMessage userMessage = AiMessage.builder()
                .conversationId(conversationId)
                .sender(AiSender.USER)
                .contentText(dto.getContent())
                .createdAt(Instant.now())
                .build();
        messageRepository.save(userMessage);

        String mockAiResponse = "[Trợ lý AI Socratic] Tôi đã ghi nhận câu hỏi của bạn: \""
                + dto.getContent()
                + "\". Hãy cùng phân tích các hiện tượng và định luật vật lý cơ bản để giải quyết bài toán này.";

        AiMessage aiMessage = AiMessage.builder()
                .conversationId(conversationId)
                .sender(AiSender.AI)
                .contentText(mockAiResponse)
                .createdAt(Instant.now().plusMillis(50))
                .build();

        AiMessage savedAiMessage = messageRepository.save(aiMessage);
        return toMessageDTO(savedAiMessage);
    }

    @Override
    public List<AiMessageDTO> getConversationHistory(UUID conversationId, UUID studentId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(conversation.getStudentId(), studentId)) {
            throw new CustomException("Access denied: You cannot view another student's conversation", HttpStatus.FORBIDDEN);
        }

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<AiConversationDTO> getMyConversations(UUID studentId) {
        return conversationRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .map(this::toConversationDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AiConversationDTO endConversation(UUID conversationId, UUID studentId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(conversation.getStudentId(), studentId)) {
            throw new CustomException("Access denied: You cannot end another student's conversation", HttpStatus.FORBIDDEN);
        }

        if (conversation.getEndedAt() == null) {
            conversation.setEndedAt(Instant.now());
            conversation = conversationRepository.save(conversation);
        }

        return toConversationDTO(conversation);
    }

    @Override
    @Transactional
    public void submitFeedback(UUID messageId, AiFeedbackDTO dto, UUID studentId) {
        AiMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException("Message not found", HttpStatus.NOT_FOUND));

        AiConversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new CustomException("Conversation not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(conversation.getStudentId(), studentId)) {
            throw new CustomException("Access denied: You cannot review another student's AI message", HttpStatus.FORBIDDEN);
        }

        AiFeedback feedback = feedbackRepository.findByMessageIdAndStudentId(messageId, studentId)
                .orElse(AiFeedback.builder()
                        .messageId(messageId)
                        .studentId(studentId)
                        .build());

        feedback.setRating(dto.getRating());
        feedback.setComment(dto.getComment());
        feedback.setCreatedAt(Instant.now());

        feedbackRepository.save(feedback);
    }

    private AiConversationDTO toConversationDTO(AiConversation c) {
        int count = messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getConversationId()).size();
        return AiConversationDTO.builder()
                .conversationId(c.getConversationId())
                .studentId(c.getStudentId())
                .classId(c.getClassId())
                .topicId(c.getTopicId())
                .mode(c.getMode())
                .startedAt(c.getStartedAt())
                .endedAt(c.getEndedAt())
                .messageCount(count)
                .build();
    }

    private AiMessageDTO toMessageDTO(AiMessage m) {
        return AiMessageDTO.builder()
                .messageId(m.getMessageId())
                .conversationId(m.getConversationId())
                .sender(m.getSender())
                .contentText(m.getContentText())
                .audioUrl(m.getAudioUrl())
                .createdAt(m.getCreatedAt())
                .build();
    }
}