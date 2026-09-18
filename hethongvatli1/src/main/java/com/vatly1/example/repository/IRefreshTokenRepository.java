package com.vatly1.example.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vatly1.example.entity.RefreshToken;

public interface IRefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Transactional
  void deleteByUsername(String username);

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Modifying
  @Query("update RefreshToken t set t.revoked = true where t.username = :username and t.revoked = false")
  int revokeAllByUsername(String username);

}


