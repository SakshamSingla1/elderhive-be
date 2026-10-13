package com.elderhive.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the REAL registered Spring Security filter chain via MockMvc (not a direct
 * doFilterInternal call like JwtAuthFilterTest) — proving SecurityConfig's wiring of
 * JwtAuthFilter + authorizeHttpRequests actually works together over a dispatched
 * request, the same path a real browser request takes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Test
    void protectedEndpointRejectsRequestsWithNoCookie() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsAValidAccessTokenCookie() throws Exception {
        User user = userRepository.save(User.builder()
                .email("security-config-test@example.com")
                .passwordHash("hashed")
                .userType(UserTypeEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build());
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

        mockMvc.perform(get("/actuator/health").cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk());
    }

    @Test
    void authPathIsPublicEvenWithoutACookie() throws Exception {
        // No /auth controller exists yet (Phase 2) - this just proves permitAll lets the
        // request reach MVC dispatch (404 "not found", not 401 "unauthorized").
        mockMvc.perform(get("/auth/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerPathIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
