package com.vatly1.example.repository;

import com.vatly1.example.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findAll(Pageable pageable);
    Page<AuditLog> findByEntity(String entity, Pageable pageable);
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);
    Page<AuditLog> findByEntityAndUserId(String entity, UUID userId, Pageable pageable);
}