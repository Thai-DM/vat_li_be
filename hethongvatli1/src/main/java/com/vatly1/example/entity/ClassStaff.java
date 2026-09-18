package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.ClassStaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "class_staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ClassStaff.ClassStaffId.class)
public class ClassStaff {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ClassStaffId implements java.io.Serializable {
        private java.util.UUID classId;
        private java.util.UUID userId;
    }
    @Id
    @Column(name = "class_id")
    private java.util.UUID classId;

    @Id
    @Column(name = "user_id")
    private java.util.UUID userId;

    @Column(name = "role_in_class")
    @Enumerated(EnumType.STRING)
    private ClassStaffRole roleInClass;


}