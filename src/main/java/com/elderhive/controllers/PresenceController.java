package com.elderhive.controllers;

import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.presence.PresenceService;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Online/offline status, kept live over /topic/presence (PresenceTracker's own broadcast on
 * every connect/disconnect transition). {@code @RestController} already carries
 * {@code @Controller} as a meta-annotation, so this same class can also host the STOMP
 * heartbeat mapping below - Spring's STOMP message dispatch and its HTTP
 * DispatcherServlet/Security filter chain are two entirely separate pathways (the class-level
 * {@code @PreAuthorize}/{@code @RequestMapping} below are inert for the STOMP method; a SEND
 * frame is only reachable at all once WebSocketJwtHandshakeInterceptor has already
 * authenticated the underlying session, same precedent ChatWebSocketController's own send()
 * relies on).
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/status")
    public ResponseEntity<ResponseModel<Map<Long, Boolean>>> getStatus(@RequestParam List<Long> userIds) {
        return ApiResponse.successResponse(presenceService.getStatus(userIds), "Presence status fetched successfully");
    }

    /** A connected client is expected to SEND here periodically to keep itself from going
     *  stale per PresenceTracker#heartbeat - silently dropped if the session has no
     *  authenticated userId (shouldn't be reachable, since WebSocketJwtHandshakeInterceptor
     *  already rejects an unauthenticated handshake before any SEND can occur). */
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long userId = sessionAttributes != null
                ? (Long) sessionAttributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID) : null;
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }
}
