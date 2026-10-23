package com.elderhive.controllers;

import com.elderhive.dtos.Messaging.MessageRequest;
import com.elderhive.exceptions.GenericException;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import com.elderhive.services.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Realtime send over STOMP (Prompt 89) - the client SENDs to
 * {@code /app/conversations/{conversationId}/send} and every subscriber of
 * {@code /topic/conversations/{conversationId}} (including the sender's own other open tab)
 * receives the persisted MessageResponse back, broadcast by MessagingServiceImpl#sendMessage
 * itself. There is deliberately no REST "send a message" endpoint - STOMP is the one path,
 * so REST and WS can never disagree about what sending a message does.
 *
 * <p>senderId comes from the authenticated WebSocket session (set by
 * WebSocketJwtHandshakeInterceptor at CONNECT time), never from the client payload - same
 * "identity comes from the session/token, not the request body" rule every REST controller
 * in this codebase already follows. A STOMP SEND has no response frame to hand a rejection
 * back on, so a failed/forbidden send is just logged and dropped, same precedent
 * ElderSphere's own typing handler uses for its own access check.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final MessagingService messagingService;

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(@DestinationVariable Long conversationId, @Payload MessageRequest request,
                      SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long senderId = sessionAttributes != null
                ? (Long) sessionAttributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID) : null;
        if (senderId == null) {
            log.warn("Rejected WS send on conversation {}: no authenticated session", conversationId);
            return;
        }
        if (!messagingService.hasAccess(senderId, conversationId)) {
            log.warn("Rejected WS send on conversation {} from user {}: not a participant", conversationId, senderId);
            return;
        }

        try {
            messagingService.sendMessage(senderId, conversationId, request);
        } catch (GenericException e) {
            log.warn("Failed to send message on conversation {} from user {}: {}", conversationId, senderId, e.getErrorMessage());
        }
    }
}
