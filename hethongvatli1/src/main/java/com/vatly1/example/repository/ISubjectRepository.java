package com.vatly1.example.repository;

import com.vatly1.example.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ISubjectRepository extends JpaRepository<Subject, java.util.UUID> {
    Page<Subject> findByIsActive(Boolean isActive, Pageable pageable);
    boolean existsBySubjectCode(String subjectCode);
}
