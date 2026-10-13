package com.elderhive.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A lightweight request/response audit log line — logged at INFO, not persisted to a
 * table (that's entities.AuditLog/AuditLogService, for compliance-sensitive entity
 * changes specifically, a different concern entirely).
 *
 * Reads "currentUserId" directly off the request rather than re-parsing the JWT itself
 * the way ElderSphere's AuditFilter does (it re-extracts from an Authorization header,
 * which this project's JwtAuthFilter doesn't even support - cookie-only, see Prompt 11).
 * This filter is wired to run AFTER JwtAuthFilter in SecurityConfig specifically so that
 * attribute is already set by the time this runs, whenever the request was authenticated.
 */
@Slf4j
@Component
public class AuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            Object userId = request.getAttribute("currentUserId");
            if (userId != null) {
                log.info("{} {} -> {} ({}ms) [userId={}]",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, userId);
            } else {
                log.info("{} {} -> {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            }
        }
    }
}
