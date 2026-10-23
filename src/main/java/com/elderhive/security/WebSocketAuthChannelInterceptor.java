package com.elderhive.security;

import com.elderhive.entities.Conversation;
import com.elderhive.repositories.ConversationRepository;
import lombok.RequiredArgsConstructor;
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
 * Started narrower than ElderSphere's own WebSocketAuthChannelInterceptor (no Conversation
 * entity existed before Prompt 89) and has grown one topic pattern at a time since, same as
 * ElderSphere's own file did: /topic/notifications/{userId} first, then Prompt 87's
 * /topic/emergency-alerts, now Prompt 89's /topic/conversations/{conversationId}
 * (participant-only, mirroring ElderSphere's own CONVERSATION_TOPIC check exactly - unlike
 * ElderSphere, there's no separate /typing variant yet, since Prompt 89 doesn't ask for a
 * typing indicator).
 *
 * <p>CONNECT is double-checked here too, though WebSocketJwtHandshakeInterceptor already
 * rejects unauthenticated sockets before they get this far. ADMIN/SUPER_ADMIN may subscribe
 * to any user's notifications topic or conversation (observability), matching ElderHive's
 * own established admin-oversight precedent elsewhere (e.g. UserThemeController's by-theme
 * lookups).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern NOTIFICATION_TOPIC = Pattern.compile("^/topic/notifications/(\\d+)$");
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/(\\d+)$");
    // The live claim/resolve/dispatch-override broadcast (EmergencyAlertServiceImpl,
    // Prompt 87) - no per-user id in the destination (everyone watching gets the same
    // message), so this is gated by ROLE instead of ownership: a FAMILY_MEMBER/ELDER
    // session has no legitimate reason to see system-wide alert status changes for
    // elders they have no connection to.
    private static final String EMERGENCY_ALERTS_TOPIC = "/topic/emergency-alerts";

    private final ConversationRepository conversationRepository;

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

            Matcher conversationMatcher = CONVERSATION_TOPIC.matcher(destination);
            if (conversationMatcher.matches()) {
                Long conversationId = Long.valueOf(conversationMatcher.group(1));
                Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
                boolean participant = conversation != null
                        && (userId.equals(conversation.getUserAId()) || userId.equals(conversation.getUserBId()));
                if (!isAdmin && !participant) {
                    log.warn("Rejected WS subscribe to {} by user {}", destination, userId);
                    throw new MessagingException("Not a participant of this conversation");
                }
            }
        }

        return message;
    }
}
