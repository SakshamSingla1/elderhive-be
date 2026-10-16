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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    // ==== GET / PUT - the caller's own inbox ====

    @Test
    void aUserSeesOnlyTheirOwnNotificationsNewestFirst() throws Exception {
        User superAdmin = persistUser("notif-inbox-sender@example.com", UserTypeEnum.SUPER_ADMIN);
        User self = persistUser("notif-inbox-self@example.com", UserTypeEnum.ELDER);
        User other = persistUser("notif-inbox-other@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);

        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CONFIRMED", other.getId(), Map.of(
                        "elderName", "X", "serviceName", "Y", "bookingDate", "Z"))));
        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CONFIRMED", self.getId(), Map.of(
                        "elderName", "Robert", "serviceName", "Visit", "bookingDate", "Oct 20"))));

        mockMvc.perform(get("/api/v1/notifications").cookie(loginCookieFor(self)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Booking confirmed"));
    }

    @Test
    void unreadCountReflectsOnlyUnreadNotificationsForTheCaller() throws Exception {
        User superAdmin = persistUser("notif-unread-sender@example.com", UserTypeEnum.SUPER_ADMIN);
        User self = persistUser("notif-unread-self@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);
        Cookie selfCookie = loginCookieFor(self);

        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CONFIRMED", self.getId(), Map.of(
                        "elderName", "A", "serviceName", "B", "bookingDate", "C"))));

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(selfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        Notification notification = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                self.getId(), PageRequest.of(0, 10)).getContent().get(0);
        mockMvc.perform(put("/api/v1/notifications/" + notification.getId() + "/read").cookie(selfCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(selfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void markingAnotherUsersNotificationAsReadReturns404() throws Exception {
        User superAdmin = persistUser("notif-mark-sender@example.com", UserTypeEnum.SUPER_ADMIN);
        User owner = persistUser("notif-mark-owner@example.com", UserTypeEnum.ELDER);
        User intruder = persistUser("notif-mark-intruder@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);

        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CONFIRMED", owner.getId(), Map.of(
                        "elderName", "A", "serviceName", "B", "bookingDate", "C"))));
        Notification notification = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                owner.getId(), PageRequest.of(0, 10)).getContent().get(0);

        mockMvc.perform(put("/api/v1/notifications/" + notification.getId() + "/read")
                        .cookie(loginCookieFor(intruder)))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllAsReadFlipsEveryUnreadNotificationForTheCaller() throws Exception {
        User superAdmin = persistUser("notif-markall-sender@example.com", UserTypeEnum.SUPER_ADMIN);
        User self = persistUser("notif-markall-self@example.com", UserTypeEnum.ELDER);
        Cookie superAdminCookie = loginCookieFor(superAdmin);
        Cookie selfCookie = loginCookieFor(self);

        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CONFIRMED", self.getId(), Map.of(
                        "elderName", "A", "serviceName", "B", "bookingDate", "C"))));
        mockMvc.perform(post("/api/v1/notifications/test").cookie(superAdminCookie).contentType("application/json")
                .content(testRequestJson("BOOKING_CANCELLED", self.getId(), Map.of(
                        "elderName", "A", "serviceName", "B", "bookingDate", "C"))));

        mockMvc.perform(put("/api/v1/notifications/read-all").cookie(selfCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(selfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void anUnauthenticatedCallerCannotReadTheInbox() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
