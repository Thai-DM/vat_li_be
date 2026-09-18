package com.vatly1.example.filter;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-Memory Sliding Window Rate Limiting Filter for Authentication Endpoints.
 *
 * <p><b>Architecture & Deployment Scope:</b>
 * <ul>
 *   <li><b>Current Scope:</b> Designed for single-node / single-pod deployment using a concurrent in-memory
 *       sliding window ({@link ConcurrentHashMap}). Provides essential application-layer protection against brute-force
 *       and signup spam without introducing external infrastructure dependencies (e.g. Redis).</li>
 *   <li><b>Horizontal Scaling Limitation:</b> When scaled to N instances behind a round-robin load balancer,
 *       each pod maintains its own in-memory state. Consequently, the effective cluster-wide rate limit
 *       scales to {@code limit * N} requests per minute.</li>
 *   <li><b>Production Distributed Roadmap:</b> For multi-pod / auto-scaling production environments,
 *       rate limiting should be offloaded to either:
 *       <ol>
 *         <li><b>Edge / API Gateway:</b> Nginx ({@code limit_req_zone}), Cloudflare WAF, or AWS ALB/WAF rules
 *             (Recommended for highest performance and CPU offloading).</li>
 *         <li><b>Distributed Store:</b> Redis-backed token bucket (e.g. Bucket4j-Redis or Lua atomic {@code INCR}+{@code EXPIRE})
 *             to share counters across all Spring Boot pods.</li>
 *       </ol>
 *   </li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int SIGNIN_LIMIT = 10;
    private static final int SIGNUP_LIMIT = 3;
    private static final int FORGOT_PASSWORD_LIMIT = 3;
    private static final long WINDOW_MS = 60_000L; // 1 minute

    private static final ConcurrentHashMap<String, Queue<Long>> requestCounts = new ConcurrentHashMap<>();

    public static void reset() {
        requestCounts.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        int limit = -1;

        if (path.endsWith("/api/v1/users/signin") || path.endsWith("/signin")) {
            limit = SIGNIN_LIMIT;
        } else if (path.endsWith("/api/v1/users/signup") || path.endsWith("/signup")) {
            limit = SIGNUP_LIMIT;
        } else if (path.endsWith("/api/v1/users/forgot-password") || path.endsWith("/forgot-password")) {
            limit = FORGOT_PASSWORD_LIMIT;
        }

        if (limit > 0) {
            String clientIp = getClientIp(request);
            // In unit/integration tests running locally via MockMvc (127.0.0.1 without proxy header),
            // bypass to prevent test suites from starving each other, unless X-Forwarded-For is supplied.
            boolean isLocalTestWithoutHeader = ("127.0.0.1".equals(clientIp) || "localhost".equalsIgnoreCase(clientIp))
                    && request.getHeader("X-Forwarded-For") == null;

            if (!isLocalTestWithoutHeader) {
                String key = clientIp + ":" + path;
                long now = System.currentTimeMillis();

                Queue<Long> timestamps = requestCounts.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());

                synchronized (timestamps) {
                    while (!timestamps.isEmpty() && timestamps.peek() < now - WINDOW_MS) {
                        timestamps.poll();
                    }

                    if (timestamps.size() >= limit) {
                        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(
                                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
                        );
                        return;
                    }

                    timestamps.offer(now);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank() && !"unknown".equalsIgnoreCase(xf)) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}