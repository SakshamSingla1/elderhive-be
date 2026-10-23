package com.elderhive.config;

import com.elderhive.presence.PresenceTracker;
import com.elderhive.security.WebSocketAuthChannelInterceptor;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP-over-WebSocket (with SockJS fallback) for real-time notification delivery. See
 * {@link WebSocketJwtHandshakeInterceptor} for how the handshake is authenticated and
 * {@link WebSocketAuthChannelInterceptor} for per-topic subscription authorization.
 *
 * <p>Topics:
 * <ul>
 *   <li>/topic/notifications/{userId} — new notifications for that user (Prompt 58's
 *       NotificationService pushes here via SimpMessagingTemplate)</li>
 *   <li>/topic/emergency-alerts — live claim/resolve/dispatch-override broadcast
 *       (Prompt 87's EmergencyAlertServiceImpl)</li>
 *   <li>/topic/conversations/{conversationId} — new chat messages (Prompt 89's
 *       MessagingServiceImpl)</li>
 *   <li>/topic/presence — online/offline transitions for any user (Prompt 90's
 *       {@link PresenceTracker}, which also listens for this same session's own CONNECT/
 *       DISCONNECT events to drive that broadcast - no extra wiring needed here beyond the
 *       broker/endpoint registration already in place)</li>
 * </ul>
 *
 * <p>The {@code /app} application destination prefix (Prompt 89) is where a client SENDs a
 * STOMP frame the server itself handles ({@code @MessageMapping}, see
 * ChatWebSocketController and, since Prompt 90, PresenceController's own heartbeat mapping)
 * - distinct from {@code /topic}, which only the server ever publishes to.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Value("${app.frontend.url:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.stream()
                .map(String::trim)
                .map(o -> o.replace("\"", "").replace("'", ""))
                .filter(o -> !o.isBlank())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
