package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "material_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialApproval {

    @Column(name = "approval_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID approvalId;

    @Column(name = "material_id")
    private java.util.UUID materialId;

    @Column(name = "reviewer_id")
    private java.util.UUID reviewerId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    @Column(name = "comment")
    private String comment;

    @Column(name = "reviewed_at")
    private java.time.Instant reviewedAt;


}
