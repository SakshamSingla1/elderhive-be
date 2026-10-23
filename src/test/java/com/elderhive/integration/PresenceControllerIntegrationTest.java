package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 90) - the REST
 * {@code /status} snapshot only, since MockMvc has no live WebSocket session to actually
 * flip a user online; that path (connect/disconnect/heartbeat, and the live /topic/presence
 * broadcast) is covered by PresenceTrackerTest and was additionally confirmed against a real
 * running server with a real STOMP client during this prompt's own verification.
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
}
