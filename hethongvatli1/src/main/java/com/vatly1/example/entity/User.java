package com.vatly1.example.entity;

import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Column(name = "user_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID userId;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;


}
