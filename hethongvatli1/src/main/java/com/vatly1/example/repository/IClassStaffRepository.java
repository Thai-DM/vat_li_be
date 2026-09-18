package com.vatly1.example.repository;

import com.vatly1.example.entity.ClassStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IClassStaffRepository extends JpaRepository<ClassStaff, ClassStaff> {
    List<ClassStaff> findByClassId(UUID classId);
    boolean existsByClassIdAndUserId(UUID classId, UUID userId);
    void deleteByClassIdAndUserId(UUID classId, UUID userId);
    Optional<ClassStaff> findByClassIdAndUserId(UUID classId, UUID userId);
}