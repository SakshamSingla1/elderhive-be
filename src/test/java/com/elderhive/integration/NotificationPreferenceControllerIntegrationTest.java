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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack for
 * GET/PUT /api/v1/notification-preferences.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationPreferenceControllerIntegrationTest {

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

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void getMineReturnsOneDefaultedRowPerNotificationTypeWhenNothingIsPersistedYet() throws Exception {
        User user = persistUser("notif-pref-defaults@example.com");

        mockMvc.perform(get("/api/v1/notification-preferences").cookie(loginCookieFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.type=='BOOKING')].inAppEnabled").value(true))
                .andExpect(jsonPath("$.data[?(@.type=='BOOKING')].emailEnabled").value(false));
    }

    @Test
    void updateMinePersistsAndIsReflectedOnTheNextGet() throws Exception {
        User user = persistUser("notif-pref-update@example.com");
        Cookie cookie = loginCookieFor(user);

        mockMvc.perform(put("/api/v1/notification-preferences").cookie(cookie).contentType("application/json")
                        .content("{\"preferences\":[{\"type\":\"EMERGENCY\",\"inAppEnabled\":true," +
                                "\"emailEnabled\":true,\"smsEnabled\":true,\"webPushEnabled\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='EMERGENCY')].smsEnabled").value(true));

        mockMvc.perform(get("/api/v1/notification-preferences").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='EMERGENCY')].emailEnabled").value(true))
                .andExpect(jsonPath("$.data[?(@.type=='EMERGENCY')].webPushEnabled").value(true));
    }

    @Test
    void anUnauthenticatedCallerCannotReadOrUpdatePreferences() throws Exception {
        mockMvc.perform(get("/api/v1/notification-preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/notification-preferences").contentType("application/json")
                        .content("{\"preferences\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
