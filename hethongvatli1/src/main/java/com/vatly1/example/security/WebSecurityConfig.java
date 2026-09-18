package com.vatly1.example.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import com.vatly1.example.filter.JwtTokenFilter;
import com.vatly1.example.utils.JwtTokenUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {

  private final JwtTokenUtils jwtTokenUtils;

  @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:*}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.cors(org.springframework.security.config.Customizer.withDefaults()); // Enable CORS
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/users/signin", "/api/v1/users/signup", "/api/v1/users/refresh",
            "/api/v1/users/forgot-password", "/api/v1/users/reset-password").permitAll()
        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/files/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/actuator/**").hasRole("ADMIN")
        .requestMatchers("/h2-console/**", "/error").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated());
    http.exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) ->
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied")));
    http.addFilterBefore(new com.vatly1.example.filter.RateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(new JwtTokenFilter(jwtTokenUtils), UsernamePasswordAuthenticationFilter.class);
    http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
    return http.build();
  }

  @Bean
  public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
    org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
    if (allowedOrigins == null || allowedOrigins.trim().isEmpty() || "*".equals(allowedOrigins.trim())) {
      configuration.setAllowedOriginPatterns(java.util.Collections.singletonList("*"));
    } else {
      java.util.List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .collect(java.util.stream.Collectors.toList());
      configuration.setAllowedOrigins(origins);
    }
    configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(java.util.Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

}



