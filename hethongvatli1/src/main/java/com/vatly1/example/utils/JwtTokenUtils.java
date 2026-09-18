package com.vatly1.example.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.SecretKey;

import lombok.RequiredArgsConstructor;
import com.vatly1.example.entity.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.vatly1.example.exception.CustomException;
import com.vatly1.example.security.MyUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class JwtTokenUtils {

  @Value("${security.jwt.token.secret-key:secret-key}")
  private String secretKey;

  @Value("${security.jwt.token.expire-length:3600000}")
  private long validityInMilliseconds = 3600000;

  private final MyUserDetails myUserDetails;

  private static final Logger log = LoggerFactory.getLogger(JwtTokenUtils.class);

  private SecretKey signingKey;

  @PostConstruct
  protected void init() {
    try {
      byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
      signingKey = Keys.hmacShaKeyFor(keyBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public String createToken(String username, UserRole role, java.util.UUID userId) {
    List<String> roleNames = Collections.singletonList("ROLE_" + role.name());
    Date now = new Date();
    Date validity = new Date(now.getTime() + validityInMilliseconds);
    return Jwts.builder()
        .subject(username)
        .claim("auth", roleNames)
        .claim("userId", userId != null ? userId.toString() : null)
        .claim("role", role != null ? role.name() : null)
        .issuedAt(now)
        .expiration(validity)
        .signWith(signingKey)
        .compact();
  }

  public long getValidityInSeconds() {
    return validityInMilliseconds / 1000;
  }

  public long getRefreshValidityInSeconds() {
    return 604800000 / 1000; // Hardcoded default for now, can be read from properties if needed
  }

  public Authentication getAuthentication(String token) {
    UserDetails userDetails = myUserDetails.loadUserByUsername(getUsername(token));
    return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
  }

  public String getUsername(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return claims.getSubject();
  }

  public String getUserId(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return claims.get("userId", String.class);
  }

  public String getRole(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return claims.get("role", String.class);
  }

  public String resolveToken(HttpServletRequest req) {
    String bearerToken = req.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      String token = bearerToken.substring(7);
      if (token.startsWith("\"") && token.endsWith("\"")) {
        token = token.substring(1, token.length() - 1);
      }
      return token;
    }
    return null;
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Invalid JWT token", e);
      throw new CustomException("Expired or invalid JWT token", HttpStatus.UNAUTHORIZED);
    }
  }

}
