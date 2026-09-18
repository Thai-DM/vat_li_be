package com.vatly1.example.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vatly1.example.exception.CustomException;
import com.vatly1.example.utils.JwtTokenUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenFilter.class);

  /**
   * Endpoints that authenticate by other means and must stay reachable with an expired access
   * token — refreshing is exactly what a client does once its access token is no longer valid, and
   * clients routinely keep sending the stale Authorization header while doing so.
   */
  private static final Set<String> UNAUTHENTICATED_PATHS =
      Set.of("/api/v1/users/signin", "/api/v1/users/signup", "/api/v1/users/refresh");

  private final JwtTokenUtils JwtTokenUtils;

  public JwtTokenFilter(JwtTokenUtils JwtTokenUtils) {
    this.JwtTokenUtils = JwtTokenUtils;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return UNAUTHENTICATED_PATHS.contains(path);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
    String token = JwtTokenUtils.resolveToken(httpServletRequest);
    try {
      if (token != null && JwtTokenUtils.validateToken(token)) {
        Authentication auth = JwtTokenUtils.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(auth);

        String userId = JwtTokenUtils.getUserId(token);
        String role = JwtTokenUtils.getRole(token);
        
        if (userId != null) {
            httpServletRequest.setAttribute("userId", userId);
        }
        if (role != null) {
            httpServletRequest.setAttribute("role", role);
        }
      }
    } catch (CustomException ex) {
      log.debug("JWT authentication failed: {}", ex.getMessage());
      // this is very important, since it guarantees the user is not authenticated at all
      SecurityContextHolder.clearContext();
      httpServletResponse.sendError(ex.getHttpStatus().value(), ex.getMessage());
      return;
    }

    filterChain.doFilter(httpServletRequest, httpServletResponse);
  }

}


