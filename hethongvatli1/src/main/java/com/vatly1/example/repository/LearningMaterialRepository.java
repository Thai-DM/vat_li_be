package com.vatly1.example.repository;

import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.MaterialScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, UUID> {

    List<LearningMaterial> findByTopicIdOrderByCreatedAtDesc(UUID topicId);
    List<LearningMaterial> findAllByOrderByCreatedAtDesc();
    List<LearningMaterial> findByTopicIdIn(List<UUID> topicIds);
    List<LearningMaterial> findByFileId(UUID fileId);
    Optional<LearningMaterial> findFirstByFileId(UUID fileId);
    Optional<LearningMaterial> findFirstByFileUrlContaining(String filename);

    // 1. SHARED Material theo Subject
    List<LearningMaterial> findBySubjectIdAndScopeOrderByCreatedAtDesc(UUID subjectId, MaterialScope scope);

    // 2. SHARED Material theo Topic
    List<LearningMaterial> findByTopicIdAndScopeOrderByCreatedAtDesc(UUID topicId, MaterialScope scope);

    // 3. CLASS Material theo classId
    List<LearningMaterial> findByClassIdAndScopeOrderByCreatedAtDesc(UUID classId, MaterialScope scope);

    // 4. CLASS Material theo classId và topicId
    List<LearningMaterial> findByClassIdAndTopicIdAndScopeOrderByCreatedAtDesc(UUID classId, UUID topicId, MaterialScope scope);

    // 5. Danh sách SHARED + CLASS theo Subject/Class
    @Query("SELECT lm FROM LearningMaterial lm WHERE lm.subjectId = :subjectId AND (lm.scope = com.vatly1.example.entity.enums.MaterialScope.SHARED OR (lm.scope = com.vatly1.example.entity.enums.MaterialScope.CLASS AND lm.classId = :classId)) ORDER BY lm.createdAt DESC")
    List<LearningMaterial> findSharedAndClassMaterials(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId);

    // 6. Truy vấn kết hợp approvalStatus
    List<LearningMaterial> findBySubjectIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(UUID subjectId, MaterialScope scope, ApprovalStatus approvalStatus);

    List<LearningMaterial> findByTopicIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(UUID topicId, MaterialScope scope, ApprovalStatus approvalStatus);

    List<LearningMaterial> findByClassIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(UUID classId, MaterialScope scope, ApprovalStatus approvalStatus);

    List<LearningMaterial> findByClassIdAndTopicIdAndScopeAndApprovalStatusOrderByCreatedAtDesc(UUID classId, UUID topicId, MaterialScope scope, ApprovalStatus approvalStatus);

    @Query("SELECT lm FROM LearningMaterial lm WHERE lm.subjectId = :subjectId AND lm.approvalStatus = :status AND (lm.scope = com.vatly1.example.entity.enums.MaterialScope.SHARED OR (lm.scope = com.vatly1.example.entity.enums.MaterialScope.CLASS AND lm.classId = :classId)) ORDER BY lm.createdAt DESC")
    List<LearningMaterial> findSharedAndClassMaterialsByStatus(@Param("subjectId") UUID subjectId, @Param("classId") UUID classId, @Param("status") ApprovalStatus status);

    // 7. Tra cứu tài liệu bằng fileId kết hợp approvalStatus
    List<LearningMaterial> findByFileIdAndApprovalStatus(UUID fileId, ApprovalStatus status);
    Optional<LearningMaterial> findFirstByFileIdAndApprovalStatus(UUID fileId, ApprovalStatus status);

    @Modifying
    @Query(value = "UPDATE learning_materials SET type = 'PDF' WHERE UPPER(type) IN ('DOCUMENT', 'DOC', 'DOCX')", nativeQuery = true)
    int migrateLegacyTypes();
}