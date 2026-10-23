package com.elderhive.security;

import com.elderhive.entities.Conversation;
import com.elderhive.repositories.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context needed) - builds raw STOMP frames via
 * StompHeaderAccessor the same way the real message channel would, with sessionAttributes
 * standing in for what WebSocketJwtHandshakeInterceptor stashed at handshake time.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private WebSocketAuthChannelInterceptor interceptor;

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

    @Test
    void aCaretakerMaySubscribeToTheEmergencyAlertsBroadcast() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/emergency-alerts", 42L, "CARETAKER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void anAdminMaySubscribeToTheEmergencyAlertsBroadcast() {
        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/emergency-alerts", 1L, "ADMIN"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void aFamilyMemberCannotSubscribeToTheEmergencyAlertsBroadcast() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/emergency-alerts", 42L, "FAMILY_MEMBER"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anElderCannotSubscribeToTheEmergencyAlertsBroadcast() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/emergency-alerts", 42L, "ELDER"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void aParticipantCanSubscribeToTheirOwnConversationTopic() {
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(
                Conversation.builder().id(5L).userAId(42L).userBId(99L).build()));

        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversations/5", 42L, "FAMILY_MEMBER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void theOtherParticipantCanAlsoSubscribeToTheConversationTopic() {
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(
                Conversation.builder().id(5L).userAId(42L).userBId(99L).build()));

        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversations/5", 99L, "CARETAKER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void aNonParticipantCannotSubscribeToTheConversationTopic() {
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(
                Conversation.builder().id(5L).userAId(42L).userBId(99L).build()));

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversations/5", 7L, "FAMILY_MEMBER"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribingToAnUnknownConversationTopicIsRejected() {
        when(conversationRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversations/5", 42L, "FAMILY_MEMBER"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void anAdminMaySubscribeToAnyConversationTopic() {
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(
                Conversation.builder().id(5L).userAId(42L).userBId(99L).build()));

        Message<?> result = interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/conversations/5", 1L, "ADMIN"), null);

        assertThat(result).isNotNull();
    }
}
