package com.vatly1.example.service;

import com.vatly1.example.dto.AiConversationDTO;
import com.vatly1.example.dto.AiFeedbackDTO;
import com.vatly1.example.dto.AiMessageDTO;
import com.vatly1.example.dto.SendAiMessageDTO;
import com.vatly1.example.dto.StartAiConversationDTO;

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
