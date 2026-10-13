package com.elderhive.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the access token from the "accessToken" cookie only — deliberately NOT from an
 * Authorization header, unlike ElderSphere's version. This matches the httpOnly-cookie
 * auth contract Phase 2's login endpoint sets (see IMPLEMENTATION_PLAN.md Prompt 16):
 * JS never touches the token directly, so there's no client-side code that would ever
 * send it as a Bearer header anyway.
 *
 * Also sets a "currentUserId" request attribute on successful auth — this is new versus
 * ElderSphere, specifically so Prompt 8's AuditorAwareImpl can populate
 * createdBy/updatedBy on every Auditable entity without each service setting it manually.
 *
 * Not yet wired into the Spring Security filter chain — that's SecurityConfig, the next
 * prompt. Being a @Component extending OncePerRequestFilter does mean Spring Boot will
 * auto-register it as a plain servlet filter in the meantime; harmless today (it only
 * ever no-ops when there's no "accessToken" cookie, which nothing issues yet) and
 * ElderSphere's own SecurityConfig doesn't guard against the resulting double-registration
 * once it's also added to the security chain either, so this mirrors that as-is rather
 * than fixing a non-problem that isn't actually part of this prompt.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.extractEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtUtil.validateToken(token, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    Long userId = jwtUtil.extractUserId(token);
                    if (userId != null) {
                        request.setAttribute("currentUserId", userId.toString());
                    }
                }
            } catch (UsernameNotFoundException e) {
                // Leave unauthenticated; downstream security config will 401/403 as appropriate.
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
