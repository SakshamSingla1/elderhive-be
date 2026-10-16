package com.elderhive.integration;

import com.elderhive.entities.Notification;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack - proves the temporary
 * POST /api/v1/notifications/test stub actually exercises NotificationService.send() for
 * real (a Notification row genuinely gets written) and is gated to SUPER_ADMIN only, exactly
 * as strict as ColorThemeController's own mutation endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private String testRequestJson(String templateKey, Long targetUserId, Map<String, String> variables)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "templateKey", templateKey,
                "targetUserId", targetUserId,
                "variables", variables
        ));
    }

    @Test
    void superAdminCanSendARealSeededTemplateAndANotificationRowIsActuallyWritten() throws Exception {
        User superAdmin = persistUser("notif-test-superadmin@example.com", UserTypeEnum.SUPER_ADMIN);
        User target = persistUser("notif-test-target@example.com", UserTypeEnum.ELDER);
        Cookie cookie = loginCookieFor(superAdmin);

        mockMvc.perform(post("/api/v1/notifications/test")
                        .cookie(cookie)
                        .contentType("application/json")
                        .content(testRequestJson("BOOKING_CONFIRMED", target.getId(), Map.of(
                                "elderName", "Robert", "serviceName", "Companionship Visit", "bookingDate", "Oct 20"
                        ))))
                .andExpect(status().isOk());

        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                target.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getTitle()).isEqualTo("Booking confirmed");
        assertThat(notification.getMessage())
                .isEqualTo("Hi Robert, your Companionship Visit booking on Oct 20 has been confirmed.");
    }

    @Test
    void aRegularAdminCannotUseTheTestEndpoint() throws Exception {
        User admin = persistUser("notif-test-admin@example.com", UserTypeEnum.ADMIN);
        User target = persistUser("notif-test-target2@example.com", UserTypeEnum.ELDER);
        Cookie cookie = loginCookieFor(admin);

        mockMvc.perform(post("/api/v1/notifications/test")
                        .cookie(cookie)
                        .contentType("application/json")
                        .content(testRequestJson("BOOKING_CONFIRMED", target.getId(), Map.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/test")
                        .contentType("application/json")
                        .content(testRequestJson("BOOKING_CONFIRMED", 1L, Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnregisteredTemplateKeyReturns404() throws Exception {
        User superAdmin = persistUser("notif-test-superadmin2@example.com", UserTypeEnum.SUPER_ADMIN);
        User target = persistUser("notif-test-target3@example.com", UserTypeEnum.ELDER);
        Cookie cookie = loginCookieFor(superAdmin);

        mockMvc.perform(post("/api/v1/notifications/test")
                        .cookie(cookie)
                        .contentType("application/json")
                        .content(testRequestJson("NO_SUCH_TEMPLATE", target.getId(), Map.of())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingVariablesFieldFailsValidationWith400() throws Exception {
        User superAdmin = persistUser("notif-test-superadmin3@example.com", UserTypeEnum.SUPER_ADMIN);
        Cookie cookie = loginCookieFor(superAdmin);

        mockMvc.perform(post("/api/v1/notifications/test")
                        .cookie(cookie)
                        .contentType("application/json")
                        .content("{\"templateKey\":\"BOOKING_CONFIRMED\",\"targetUserId\":1}"))
                .andExpect(status().isBadRequest());
    }
}
