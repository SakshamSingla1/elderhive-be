package com.elderhive.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads the "currentUserId" request attribute that JwtAuthFilter sets once it exists
 * (Phase 1, a later prompt). Until then — and for any request with no authenticated
 * user (e.g. registration, login) — this deliberately returns Optional.empty() rather
 * than a hardcoded fallback id, so createdBy/updatedBy just stay null instead of lying
 * about who made the change.
 */
@Slf4j
@Component
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        try {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                Object currentUserId = request.getAttribute("currentUserId");
                if (currentUserId != null) {
                    return Optional.of(Long.parseLong(currentUserId.toString()));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get user ID from request: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
