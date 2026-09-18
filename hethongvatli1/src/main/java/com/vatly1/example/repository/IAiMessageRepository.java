package com.vatly1.example.repository;

import com.vatly1.example.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IAiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
