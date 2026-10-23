package com.elderhive.integration;

import com.elderhive.controllers.PresenceController;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.presence.PresenceTracker;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectEvent;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 90) - the REST
 * {@code /status} snapshot, PLUS (added Prompt 92, after a real bug was found live) a check
 * that calling {@code @Autowired PresenceController}'s own heartbeat() - the REAL
 * Spring-proxied bean, not a plain `new PresenceController(...)` like PresenceControllerTest
 * uses - doesn't throw. A class-level {@code @PreAuthorize} on this controller used to wrap
 * heartbeat() too (Spring's AOP method interceptor fires for ANY invocation of the proxied
 * bean, regardless of entry point - unlike the HTTP-only filter chain), and every real STOMP
 * heartbeat failed with AuthenticationCredentialsNotFoundException since STOMP message
 * handling never populates SecurityContextHolder. A pure Mockito unit test
 * (PresenceControllerTest) can never catch this class of bug, since it never goes through
 * Spring's real AOP proxy - only a test like this one, autowiring the real bean from a real
 * ApplicationContext, exercises the actual security interceptor chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresenceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PresenceController presenceController;

    @Autowired
    private PresenceTracker presenceTracker;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void anAuthenticatedCallerGetsAFalseStatusForEveryRequestedUserWithNoLiveSession() throws Exception {
        User caller = persistUser("presence-status-caller@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerA = persistUser("presence-status-caretakerA@example.com", UserTypeEnum.CARETAKER);
        User caretakerB = persistUser("presence-status-caretakerB@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(get("/api/v1/presence/status")
                        .cookie(loginCookieFor(caller))
                        .param("userIds", caretakerA.getId().toString(), caretakerB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + caretakerA.getId() + "']").value(false))
                .andExpect(jsonPath("$.data['" + caretakerB.getId() + "']").value(false));
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/presence/status").param("userIds", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRealProxiedBeansHeartbeatMethodDoesNotThrowEvenThoughNoHttpAuthenticationExists() {
        User caretakerUser = persistUser("presence-heartbeat-caretaker@example.com", UserTypeEnum.CARETAKER);

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionAttributes(Map.of(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, caretakerUser.getId()));
        presenceTracker.onConnect(new SessionConnectEvent(this, MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders())));
        assertThat(presenceTracker.isOnline(caretakerUser.getId())).isTrue();

        SimpMessageHeaderAccessor heartbeatAccessor = SimpMessageHeaderAccessor.create();
        heartbeatAccessor.setSessionAttributes(Map.of(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, caretakerUser.getId()));

        // Would throw AuthenticationCredentialsNotFoundException if @PreAuthorize were still
        // wrongly applied at the class level - SecurityContextHolder has nothing in it here,
        // same as a real STOMP SEND frame.
        presenceController.heartbeat(heartbeatAccessor);

        assertThat(presenceTracker.isOnline(caretakerUser.getId())).isTrue();
    }
}
