package com.elderhive.controllers;

import com.elderhive.dtos.Messaging.MessageRequest;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import com.elderhive.services.MessagingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context needed) - builds a bare SimpMessageHeaderAccessor with
 * sessionAttributes standing in for what WebSocketJwtHandshakeInterceptor stashed at
 * handshake time, same style WebSocketAuthChannelInterceptorTest uses for its own frames.
 */
@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private MessagingService messagingService;

    @InjectMocks
    private ChatWebSocketController controller;

    private SimpMessageHeaderAccessor accessorFor(Long userId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (userId != null) sessionAttributes.put(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, userId);
        accessor.setSessionAttributes(sessionAttributes);
        return accessor;
    }

    @Test
    void aParticipantsSendIsPersistedThroughMessagingService() throws GenericException {
        when(messagingService.hasAccess(42L, 10L)).thenReturn(true);
        MessageRequest request = MessageRequest.builder().content("Hi there").build();

        controller.send(10L, request, accessorFor(42L));

        verify(messagingService).sendMessage(eq(42L), eq(10L), eq(request));
    }

    @Test
    void aSendWithNoAuthenticatedSessionIsDroppedWithoutCallingTheService() throws GenericException {
        controller.send(10L, MessageRequest.builder().content("Hi").build(), accessorFor(null));

        verify(messagingService, never()).sendMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aNonParticipantsSendIsDroppedWithoutCallingSendMessage() throws GenericException {
        when(messagingService.hasAccess(7L, 10L)).thenReturn(false);

        controller.send(10L, MessageRequest.builder().content("Hi").build(), accessorFor(7L));

        verify(messagingService, never()).sendMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aGenericExceptionFromSendMessageIsSwallowedRatherThanPropagated() throws GenericException {
        when(messagingService.hasAccess(42L, 10L)).thenReturn(true);
        when(messagingService.sendMessage(eq(42L), eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new GenericException(ExceptionCodeEnum.BAD_REQUEST, "Message must have content or an attachment"));

        controller.send(10L, MessageRequest.builder().build(), accessorFor(42L));
    }
}
