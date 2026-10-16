package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.webpush.VapidKeyProvider;
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

/** End-to-end for GET /api/v1/push-subscriptions/vapid-public-key. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushSubscriptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VapidKeyProvider vapidKeyProvider;

    @Test
    void anAuthenticatedCallerReceivesTheRealServerVapidPublicKey() throws Exception {
        User user = userRepository.save(User.builder()
                .email("vapid-key-test@example.com").passwordHash("hashed")
                .userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build());
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

        mockMvc.perform(get("/api/v1/push-subscriptions/vapid-public-key")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicKey").value(vapidKeyProvider.getPublicKey()));
    }

    @Test
    void anUnauthenticatedCallerCannotFetchTheVapidPublicKey() throws Exception {
        mockMvc.perform(get("/api/v1/push-subscriptions/vapid-public-key"))
                .andExpect(status().isUnauthorized());
    }
}
