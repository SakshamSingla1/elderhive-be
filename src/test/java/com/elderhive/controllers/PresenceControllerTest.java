package com.elderhive.controllers;

import com.elderhive.presence.PresenceService;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pure unit test for the STOMP heartbeat mapping (no Spring context needed) - the REST
 * {@code /status} endpoint is thin enough to be covered end-to-end by
 * PresenceControllerIntegrationTest instead.
 */
@ExtendWith(MockitoExtension.class)
class PresenceControllerTest {

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private PresenceController controller;

    private SimpMessageHeaderAccessor accessorFor(Long userId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (userId != null) sessionAttributes.put(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, userId);
        accessor.setSessionAttributes(sessionAttributes);
        return accessor;
    }

    @Test
    void anAuthenticatedSessionsHeartbeatReachesPresenceService() {
        controller.heartbeat(accessorFor(42L));

        verify(presenceService).heartbeat(42L);
    }

    @Test
    void aHeartbeatWithNoAuthenticatedSessionIsDropped() {
        controller.heartbeat(accessorFor(null));

        verify(presenceService, never()).heartbeat(org.mockito.ArgumentMatchers.any());
    }
}
