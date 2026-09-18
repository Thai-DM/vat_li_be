package com.vatly1.example.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.vatly1.example.exception.CustomException;
import com.vatly1.example.entity.RefreshToken;
import com.vatly1.example.repository.IRefreshTokenRepository;
import com.vatly1.example.service.IRefreshTokenService;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements IRefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

  private final IRefreshTokenRepository refreshTokenRepository;

  @Value("${security.jwt.token.refresh-validity-in-seconds:2592000}")
  private long validityInSeconds;

  private String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encodedhash);
    } catch (Exception e) {
      throw new RuntimeException("Failed to hash token", e);
    }
  }

  @Override
  @Transactional
  public String issue(String username) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = hash(rawToken);

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setTokenHash(tokenHash);
    refreshToken.setUsername(username);
    refreshToken.setExpiryDate(Instant.now().plus(validityInSeconds, ChronoUnit.SECONDS));
    refreshToken.setRevoked(false);
    refreshTokenRepository.save(refreshToken);
    log.info("Issued fresh refresh token for user: {}", username);
    return rawToken;
  }

  @Override
  @Transactional(noRollbackFor = CustomException.class)
  public RefreshTokenServiceRotation rotate(String oldRawToken) {
    String tokenHash = hash(oldRawToken);
    RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new CustomException("Expired or invalid refresh token", HttpStatus.UNAUTHORIZED));

    if (refreshToken.isExpired() || refreshToken.isRevoked()) {
      if (refreshToken.isRevoked()) {
        log.warn("Token reuse detected for user {}. Revoking all tokens.", refreshToken.getUsername());
        refreshTokenRepository.revokeAllByUsername(refreshToken.getUsername());
      } else {
        refreshTokenRepository.delete(refreshToken);
      }
      throw new CustomException("Expired, revoked or invalid refresh token", HttpStatus.UNAUTHORIZED);
    }

    String username = refreshToken.getUsername();
    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);

    String newRawToken = issue(username);
    log.info("Rotated refresh token for user: {}", username);
    return new RefreshTokenServiceRotation(username, newRawToken);
  }

  @Override
  @Transactional
  public void revoke(String rawToken) {
    String tokenHash = hash(rawToken);
    refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
        t.setRevoked(true);
        refreshTokenRepository.save(t);
        log.info("Revoked refresh token");
    });
  }

  @Override
  @Transactional
  public void deleteAllForUser(String username) {
    refreshTokenRepository.revokeAllByUsername(username);
  }
}

