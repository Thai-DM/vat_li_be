package com.vatly1.example.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vatly1.example.entity.UserProfile;

public interface IUserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByStudentCode(String studentCode);
    boolean existsByStudentCode(String studentCode);
}