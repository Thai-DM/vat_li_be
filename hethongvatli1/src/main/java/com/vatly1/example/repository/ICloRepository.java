package com.vatly1.example.repository;

import com.vatly1.example.entity.Clo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICloRepository extends JpaRepository<Clo, UUID>, JpaSpecificationExecutor<Clo> {

    List<Clo> findBySubjectIdOrderByOrderIndexAsc(UUID subjectId);

    List<Clo> findBySubjectIdAndIsActiveTrueOrderByOrderIndexAsc(UUID subjectId);

    Optional<Clo> findBySubjectIdAndCloCode(UUID subjectId, String cloCode);

    boolean existsBySubjectIdAndCloCode(UUID subjectId, String cloCode);

    boolean existsBySubjectIdAndCloCodeAndCloIdNot(UUID subjectId, String cloCode, UUID cloId);

    long countBySubjectId(UUID subjectId);
}
