package com.elderhive.presence;

import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory only - a user's online/offline status resets on app restart, same precedent as
 * ElderSphere's own PresenceTracker (and there's no "presence" table/migration in this
 * prompt on purpose). Mirrors ElderSphere's connect/disconnect session-counting (multiple
 * open tabs/devices for one user only flip them offline once the LAST one disconnects), but
 * adds one capability ElderSphere's own version doesn't have: heartbeat-based staleness.
 *
 * <p>Why: a STOMP session can die without a clean DISCONNECT frame (an abrupt network drop,
 * a phone backgrounded mid-sleep) - Spring's own transport eventually notices and fires
 * {@link SessionDisconnectEvent} too, but only after its own, longer heartbeat-timeout
 * negotiated at the STOMP protocol level. {@link #heartbeat} gives presence-consuming code
 * (Phase 11's emergency proximity matching, most importantly) a faster, presence-specific
 * backstop: a connected session whose last heartbeat is older than {@link #STALE_AFTER} is
 * treated as offline by {@link #isOnline} even though its session count hasn't reached zero
 * yet. A caller (PresenceController's own STOMP heartbeat mapping) is expected to ping this
 * periodically from an open client; nothing here schedules that itself.
 */
@Component
@RequiredArgsConstructor
public class PresenceTracker {

    private static final Duration STALE_AFTER = Duration.ofSeconds(45);
    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    private final ConcurrentHashMap<Long, AtomicInteger> sessionCountsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> lastHeartbeatByUser = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        Long userId = resolveUserId(event.getMessage());
        if (userId == null) {
            return;
        }
        lastHeartbeatByUser.put(userId, Instant.now(clock));
        AtomicInteger count = sessionCountsByUser.computeIfAbsent(userId, id -> new AtomicInteger(0));
        // Only the first tab/device for a user should flip them online; later tabs just add to the count.
        if (count.incrementAndGet() == 1) {
            broadcast(userId, true);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = resolveUserId(event.getMessage());
        if (userId == null) {
            return;
        }
        AtomicInteger count = sessionCountsByUser.get(userId);
        if (count == null) {
            return;
        }
        // Symmetric to onConnect: only broadcast offline once the user's last open tab/device disconnects.
        if (count.updateAndGet(v -> Math.max(0, v - 1)) == 0) {
            sessionCountsByUser.remove(userId, count);
            lastHeartbeatByUser.remove(userId);
            broadcast(userId, false);
        }
    }

    /** Refreshes userId's last-seen timestamp. A no-op for a user with no currently tracked
     *  session - there's nothing to keep alive, and a stray heartbeat must never itself
     *  manufacture an "online" session that connect/disconnect never established. */
    public void heartbeat(Long userId) {
        if (userId != null && sessionCountsByUser.containsKey(userId)) {
            lastHeartbeatByUser.put(userId, Instant.now(clock));
        }
    }

    public boolean isOnline(Long userId) {
        AtomicInteger count = sessionCountsByUser.get(userId);
        if (count == null || count.get() <= 0) {
            return false;
        }
        Instant lastHeartbeat = lastHeartbeatByUser.get(userId);
        return lastHeartbeat != null && Duration.between(lastHeartbeat, Instant.now(clock)).compareTo(STALE_AFTER) <= 0;
    }

    private void broadcast(Long userId, boolean online) {
        messagingTemplate.convertAndSend(PRESENCE_TOPIC, Map.of("userId", userId, "online", online));
    }

    private Long resolveUserId(Message<byte[]> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null ? (Long) sessionAttributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID) : null;
    }
}
