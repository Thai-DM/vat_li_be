package com.vatly1.example.repository;

import com.vatly1.example.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>, JpaSpecificationExecutor<ActivityLog> {
    List<ActivityLog> findByClassIdOrderByCreatedAtDesc(UUID classId);
    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<ActivityLog> findAll(Pageable pageable);
    Page<ActivityLog> findByUserId(UUID userId, Pageable pageable);
    Page<ActivityLog> findByActionType(String actionType, Pageable pageable);
    Page<ActivityLog> findByUserIdAndActionType(UUID userId, String actionType, Pageable pageable);
}