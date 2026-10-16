package com.elderhive.security;

import com.elderhive.entities.User;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Ported close to verbatim from ElderSphere's WebSocketJwtHandshakeInterceptor, adapted for
 * ElderHive's own httpOnly-cookie auth contract (see JwtAuthFilter): the token is read from
 * the "accessToken" cookie on the handshake's underlying HttpServletRequest, not from a
 * ?token= query param or Authorization header — ElderSphere needs the query-param fallback
 * because a browser can't attach custom headers to the SockJS handshake request, but a
 * cookie is sent by the browser automatically on that same request, so there's nothing to
 * work around here. Uses UserRepository directly (no UserDao exists in ElderHive).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_USER_TYPE = "userType";

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: no accessToken cookie supplied");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String email;
        try {
            email = jwtUtil.extractEmail(token);
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: unparsable token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (email == null || !jwtUtil.validateToken(token, email)) {
            log.warn("WebSocket handshake rejected: invalid/expired token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("WebSocket handshake rejected: user not found for token subject");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_EMAIL, user.getEmail());
        attributes.put(ATTR_USER_TYPE,
                user.getUserType() != null ? user.getUserType().name() : UserTypeEnum.FAMILY_MEMBER.name());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }
}
