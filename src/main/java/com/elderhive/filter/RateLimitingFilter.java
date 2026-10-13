package com.elderhive.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Narrower than ElderSphere's version on purpose: only /auth/** is rate-limited for
 * now — the other endpoint categories ElderSphere also limits (emergency alerts,
 * contact-us, search) don't exist in this project yet. Add their own bucket rules
 * here once those modules land, following the same resolveBucket pattern.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT_TOKENS = 10;
    private static final Duration AUTH_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/auth/")) {
            String ip = getClientIp(request);
            Bucket bucket = resolveBucket("auth:" + ip, AUTH_LIMIT_TOKENS, AUTH_LIMIT_WINDOW);

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"FAILED\",\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Bucket resolveBucket(String key, int tokens, Duration duration) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(tokens, Refill.greedy(tokens, duration));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank() && isTrustedProxy(remoteAddr)) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        return ip != null && (ip.startsWith("10.") || ip.startsWith("172.")
                || ip.startsWith("192.168.") || ip.equals("127.0.0.1")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1"));
    }
}
