package com.vatly1.example.repository;

import com.vatly1.example.entity.MaterialProgress;
import com.vatly1.example.entity.enums.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialProgressRepository extends JpaRepository<MaterialProgress, UUID> {

    List<MaterialProgress> findByStudentIdAndClassId(UUID studentId, UUID classId);

    List<MaterialProgress> findByStudentIdAndClassIdAndTopicId(UUID studentId, UUID classId, UUID topicId);

    Optional<MaterialProgress> findByStudentIdAndClassIdAndMaterialId(UUID studentId, UUID classId, UUID materialId);

    List<MaterialProgress> findByClassIdAndMaterialId(UUID classId, UUID materialId);

    List<MaterialProgress> findByClassId(UUID classId);

    List<MaterialProgress> findByStudentId(UUID studentId);

    long countByStudentIdAndClassIdAndTopicIdAndStatus(UUID studentId, UUID classId, UUID topicId, ProgressStatus status);

    @Query("SELECT mp.materialId FROM MaterialProgress mp WHERE mp.studentId = :studentId AND mp.classId = :classId AND mp.topicId = :topicId AND mp.status = com.vatly1.example.entity.enums.ProgressStatus.COMPLETED")
    List<UUID> findCompletedMaterialIdsByStudentIdAndClassIdAndTopicId(
            @Param("studentId") UUID studentId,
            @Param("classId") UUID classId,
            @Param("topicId") UUID topicId);

    @Query("SELECT mp.materialId FROM MaterialProgress mp WHERE mp.studentId = :studentId AND mp.classId = :classId AND mp.status = com.vatly1.example.entity.enums.ProgressStatus.COMPLETED")
    List<UUID> findCompletedMaterialIdsByStudentIdAndClassId(
            @Param("studentId") UUID studentId,
            @Param("classId") UUID classId);
}
