package com.elderhive.presence;

import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests (no Spring context needed) - builds raw SessionConnectEvent/
 * SessionDisconnectEvent the same way Spring's own STOMP sub-protocol handler would, with
 * sessionAttributes standing in for what WebSocketJwtHandshakeInterceptor stashed at
 * handshake time (same style WebSocketAuthChannelInterceptorTest uses for its own frames).
 * A MutableClock lets the heartbeat-staleness window be advanced deterministically instead
 * of a real 45s sleep.
 */
@ExtendWith(MockitoExtension.class)
class PresenceTrackerTest {

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceBy(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final MutableClock clock = new MutableClock();

    // @InjectMocks can't see the plain (non-@Mock) MutableClock field above - construct
    // directly via the constructor Lombok generates for PresenceTracker's two final fields.
    private PresenceTracker trackerWithClock() {
        return new PresenceTracker(messagingTemplate, clock);
    }

    private Message<byte[]> messageFor(Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (userId != null) sessionAttributes.put(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, userId);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private SessionConnectEvent connectEventFor(Long userId) {
        return new SessionConnectEvent(this, messageFor(userId));
    }

    private SessionDisconnectEvent disconnectEventFor(Long userId) {
        return new SessionDisconnectEvent(this, messageFor(userId), "session-id", CloseStatus.NORMAL);
    }

    @Test
    void aUserIsOfflineBeforeAnyConnectEvent() {
        PresenceTracker tracker = trackerWithClock();

        assertThat(tracker.isOnline(42L)).isFalse();
    }

    @Test
    void connectingFlipsAUserOnlineAndBroadcastsIt() {
        PresenceTracker tracker = trackerWithClock();

        tracker.onConnect(connectEventFor(42L));

        assertThat(tracker.isOnline(42L)).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), eq(Map.of("userId", 42L, "online", true)));
    }

    @Test
    void aSecondSessionForTheSameUserDoesNotRebroadcastOnline() {
        PresenceTracker tracker = trackerWithClock();

        tracker.onConnect(connectEventFor(42L));
        tracker.onConnect(connectEventFor(42L));

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), eq(Map.of("userId", 42L, "online", true)));
    }

    @Test
    void onlyTheLastSessionDisconnectingFlipsTheUserOfflineAndBroadcastsIt() {
        PresenceTracker tracker = trackerWithClock();
        tracker.onConnect(connectEventFor(42L));
        tracker.onConnect(connectEventFor(42L));

        tracker.onDisconnect(disconnectEventFor(42L));
        assertThat(tracker.isOnline(42L)).isTrue();
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/presence"), eq(Map.of("userId", 42L, "online", false)));

        tracker.onDisconnect(disconnectEventFor(42L));
        assertThat(tracker.isOnline(42L)).isFalse();
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), eq(Map.of("userId", 42L, "online", false)));
    }

    @Test
    void disconnectingAUserWithNoTrackedSessionIsANoOp() {
        PresenceTracker tracker = trackerWithClock();

        tracker.onDisconnect(disconnectEventFor(42L));

        assertThat(tracker.isOnline(42L)).isFalse();
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/presence"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void eventsWithNoAuthenticatedUserIdAreIgnored() {
        PresenceTracker tracker = trackerWithClock();

        tracker.onConnect(connectEventFor(null));
        tracker.onDisconnect(disconnectEventFor(null));

        verify(messagingTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void aConnectedSessionGoesOfflineOnceItsHeartbeatGoesStale() {
        PresenceTracker tracker = trackerWithClock();
        tracker.onConnect(connectEventFor(42L));
        assertThat(tracker.isOnline(42L)).isTrue();

        clock.advanceBy(java.time.Duration.ofSeconds(44));
        assertThat(tracker.isOnline(42L)).isTrue();

        clock.advanceBy(java.time.Duration.ofSeconds(2));
        assertThat(tracker.isOnline(42L)).isFalse();
    }

    @Test
    void aHeartbeatBeforeStalenessKeepsTheSessionOnlinePastTheOriginalWindow() {
        PresenceTracker tracker = trackerWithClock();
        tracker.onConnect(connectEventFor(42L));

        clock.advanceBy(java.time.Duration.ofSeconds(40));
        tracker.heartbeat(42L);
        clock.advanceBy(java.time.Duration.ofSeconds(40));

        assertThat(tracker.isOnline(42L)).isTrue();
    }

    @Test
    void aHeartbeatForAUserWithNoConnectedSessionIsANoOp() {
        PresenceTracker tracker = trackerWithClock();

        tracker.heartbeat(42L);

        assertThat(tracker.isOnline(42L)).isFalse();
    }

    @Test
    void aHeartbeatForANullUserIdIsANoOp() {
        PresenceTracker tracker = trackerWithClock();

        tracker.heartbeat(null);
    }
}
