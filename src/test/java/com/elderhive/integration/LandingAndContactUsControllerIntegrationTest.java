package com.elderhive.integration;

import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers what the unit tests (LandingPageServiceImplTest/ContactUsServiceImplTest) can't:
 * that GET /api/v1/landing/page and POST /api/v1/contact-us are actually reachable with NO
 * JWT cookie (SecurityConfig's permitAll, Prompt 99's own addition), that every other
 * route under both controllers stays behind the default authenticated()+LANDING_MANAGE/
 * CONTACT_US_MANAGE gate, and that AdminBootstrapSeeder's curated "Admin" role genuinely
 * holds both new permissions (not just that they exist in the catalog).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LandingAndContactUsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistAdmin(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).status(UserStatusEnum.ACTIVE)
                .roleId(adminRole.getId()).build());
    }

    @Test
    void publicLandingPageIsReachableWithoutAnyAuthenticationCookie() throws Exception {
        mockMvc.perform(get("/api/v1/landing/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.features").isArray());
    }

    @Test
    void publicContactFormSubmissionIsReachableWithoutAnyAuthenticationCookie() throws Exception {
        Map<String, String> body = Map.of("name", "Jane", "email", "jane@example.com", "message", "Hello there");

        mockMvc.perform(post("/api/v1/contact-us")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    @Test
    void adminLandingAndContactUsEndpointsRejectAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/v1/landing/features")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/contact-us")).andExpect(status().isUnauthorized());
    }

    @Test
    void theCuratedAdminRoleCanManageLandingConfigAndTriageContactSubmissions() throws Exception {
        User admin = persistAdmin("landing-admin@example.com");
        Cookie cookie = loginCookieFor(admin);

        mockMvc.perform(put("/api/v1/landing/config")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("heroHeadline", "Welcome to ElderHive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heroHeadline").value("Welcome to ElderHive"));

        mockMvc.perform(get("/api/v1/contact-us").cookie(cookie))
                .andExpect(status().isOk());
    }
}
