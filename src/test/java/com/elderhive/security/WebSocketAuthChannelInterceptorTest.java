package com.elderhive.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests (no Spring context needed) - builds raw STOMP frames via
 * StompHeaderAccessor the same way the real message channel would, with sessionAttributes
 * standing in for what WebSocketJwtHandshakeInterceptor stashed at handshake time.
 */
class WebSocketAuthChannelInterceptorTest {

    private final WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor();

    private Message<byte[]> frame(StompCommand command, String destination, Long userId, String userType) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (userId != null) sessionAttributes.put(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, userId);
        if (userType != null) sessionAttributes.put(WebSocketJwtHandshakeInterceptor.ATTR_USER_TYPE, userType);
        accessor.setSessionAttributes(sessionAttributes);
        if (destination != null) accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void aUserCanSubscribeToTheirOwnNotificationsTopic() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/notifications/42", 42L, "ELDER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void aUserCannotSubscribeToAnotherUsersNotificationsTopic() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/notifications/99", 42L, "ELDER"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anAdminMaySubscribeToAnyUsersNotificationsTopic() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/notifications/99", 1L, "ADMIN"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void aSuperAdminMaySubscribeToAnyUsersNotificationsTopic() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/notifications/99", 1L, "SUPER_ADMIN"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void anUnauthenticatedSubscribeAttemptIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/notifications/42", null, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anUnauthenticatedConnectAttemptIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.CONNECT, null, null, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anAuthenticatedConnectIsAllowedThrough() {
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, null, 42L, "ELDER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribingToAnUnrelatedDestinationIsLeftUnchecked() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/presence", 42L, "ELDER"), null);

        assertThat(result).isNotNull();
    }
}
