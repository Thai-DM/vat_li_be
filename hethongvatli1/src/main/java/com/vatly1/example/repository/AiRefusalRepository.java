package com.vatly1.example.repository;

import com.vatly1.example.entity.AiRefusal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiRefusalRepository extends JpaRepository<AiRefusal, UUID> {

    List<AiRefusal> findByMessageId(UUID messageId);
}
