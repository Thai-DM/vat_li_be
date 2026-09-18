package com.vatly1.example.service;

import com.vatly1.example.model.dto.AiConversationDTO;
import com.vatly1.example.model.dto.AiFeedbackDTO;
import com.vatly1.example.model.dto.AiMessageDTO;
import com.vatly1.example.model.request.SendAiMessageDTO;
import com.vatly1.example.model.request.StartAiConversationDTO;

import java.util.List;
import java.util.UUID;

public interface IAiTutorService {
    AiConversationDTO startConversation(StartAiConversationDTO dto, UUID studentId);
    AiMessageDTO sendMessage(UUID conversationId, SendAiMessageDTO dto, UUID studentId);
    List<AiMessageDTO> getConversationHistory(UUID conversationId, UUID studentId);
    List<AiConversationDTO> getMyConversations(UUID studentId);
    AiConversationDTO endConversation(UUID conversationId, UUID studentId);
    void submitFeedback(UUID messageId, AiFeedbackDTO dto, UUID studentId);
}