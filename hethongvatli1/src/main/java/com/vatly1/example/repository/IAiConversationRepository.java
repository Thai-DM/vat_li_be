package com.vatly1.example.repository;

import com.vatly1.example.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IAiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByStudentIdOrderByStartedAtDesc(UUID studentId);
    Optional<AiConversation> findByConversationIdAndStudentId(UUID conversationId, UUID studentId);
    List<AiConversation> findByClassId(UUID classId);
}
