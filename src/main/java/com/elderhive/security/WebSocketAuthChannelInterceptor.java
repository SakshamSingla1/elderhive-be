package com.elderhive.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrower than ElderSphere's WebSocketAuthChannelInterceptor on purpose: ElderSphere also
 * guards /topic/conversations/{id} and its /typing variant (participant-only), but ElderHive
 * has no Conversation entity yet — this interceptor's scope was originally just
 * /topic/notifications/{userId}; Prompt 87 adds one more pattern for the same reason
 * ElderSphere's own file grew one topic at a time.
 *
 * <p>CONNECT is double-checked here too, though WebSocketJwtHandshakeInterceptor already
 * rejects unauthenticated sockets before they get this far. ADMIN/SUPER_ADMIN may subscribe
 * to any user's notifications topic (observability), matching ElderHive's own established
 * admin-oversight precedent elsewhere (e.g. UserThemeController's by-theme lookups).
 */
@Component
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern NOTIFICATION_TOPIC = Pattern.compile("^/topic/notifications/(\\d+)$");
    // The live claim/resolve/dispatch-override broadcast (EmergencyAlertServiceImpl,
    // Prompt 87) - no per-user id in the destination (everyone watching gets the same
    // message), so this is gated by ROLE instead of ownership: a FAMILY_MEMBER/ELDER
    // session has no legitimate reason to see system-wide alert status changes for
    // elders they have no connection to.
    private static final String EMERGENCY_ALERTS_TOPIC = "/topic/emergency-alerts";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Long userId = sessionAttributes != null
                ? (Long) sessionAttributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID) : null;
        String userType = sessionAttributes != null
                ? (String) sessionAttributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_TYPE) : null;
        boolean isAdmin = "ADMIN".equals(userType) || "SUPER_ADMIN".equals(userType);

        if (StompCommand.CONNECT.equals(accessor.getCommand()) && userId == null) {
            throw new MessagingException("Unauthenticated WebSocket connection");
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (userId == null) {
                throw new MessagingException("Unauthenticated subscription attempt");
            }
            String destination = accessor.getDestination();
            if (destination == null) {
                return message;
            }

            Matcher notificationMatcher = NOTIFICATION_TOPIC.matcher(destination);
            if (notificationMatcher.matches()) {
                Long topicUserId = Long.valueOf(notificationMatcher.group(1));
                if (!isAdmin && !userId.equals(topicUserId)) {
                    log.warn("Rejected WS subscribe to {} by user {}", destination, userId);
                    throw new MessagingException("Cannot subscribe to another user's notifications");
                }
            }

            if (EMERGENCY_ALERTS_TOPIC.equals(destination)) {
                boolean isCaretaker = "CARETAKER".equals(userType);
                if (!isAdmin && !isCaretaker) {
                    log.warn("Rejected WS subscribe to {} by user {}", destination, userId);
                    throw new MessagingException("Cannot subscribe to the emergency alerts broadcast");
                }
            }
        }

        return message;
    }
}
