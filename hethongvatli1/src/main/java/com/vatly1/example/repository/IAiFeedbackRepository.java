package com.vatly1.example.repository;

import com.vatly1.example.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IAiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {
    List<AiFeedback> findByStudentId(UUID studentId);
    Optional<AiFeedback> findByMessageIdAndStudentId(UUID messageId, UUID studentId);
}
