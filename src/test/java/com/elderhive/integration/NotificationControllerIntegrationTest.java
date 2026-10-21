package com.elderhive.integration;

import com.elderhive.entities.Notification;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.services.NotificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack - covers GET/PUT's own
 * "caller's own inbox" surface. Prompt 59's temporary POST /test stub (previously used here
 * purely to seed a Notification row to then test against) is gone (Prompt 78) now that
 * BookingServiceImpl is a real caller - these tests seed via NotificationService.send()
 * directly instead, which is already covered end to end by its own
 * NotificationServiceImplTest/NotificationServiceImplIntegrationTest.
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
    private NotificationService notificationService;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private void seedNotification(Long targetUserId, Map<String, String> variables) throws Exception {
        notificationService.send("BOOKING_CONFIRMED", variables, targetUserId);
    }

    @Test
    void aUserSeesOnlyTheirOwnNotificationsNewestFirst() throws Exception {
        User self = persistUser("notif-inbox-self@example.com", UserTypeEnum.ELDER);
        User other = persistUser("notif-inbox-other@example.com", UserTypeEnum.ELDER);

        seedNotification(other.getId(), Map.of("elderName", "X", "serviceName", "Y", "bookingDate", "Z"));
        seedNotification(self.getId(), Map.of("elderName", "Robert", "serviceName", "Visit", "bookingDate", "Oct 20"));

        mockMvc.perform(get("/api/v1/notifications").cookie(loginCookieFor(self)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Booking confirmed"));
    }

    @Test
    void unreadCountReflectsOnlyUnreadNotificationsForTheCaller() throws Exception {
        User self = persistUser("notif-unread-self@example.com", UserTypeEnum.ELDER);
        Cookie selfCookie = loginCookieFor(self);

        seedNotification(self.getId(), Map.of("elderName", "A", "serviceName", "B", "bookingDate", "C"));

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
        User owner = persistUser("notif-mark-owner@example.com", UserTypeEnum.ELDER);
        User intruder = persistUser("notif-mark-intruder@example.com", UserTypeEnum.ELDER);

        seedNotification(owner.getId(), Map.of("elderName", "A", "serviceName", "B", "bookingDate", "C"));
        Notification notification = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                owner.getId(), PageRequest.of(0, 10)).getContent().get(0);

        mockMvc.perform(put("/api/v1/notifications/" + notification.getId() + "/read")
                        .cookie(loginCookieFor(intruder)))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllAsReadFlipsEveryUnreadNotificationForTheCaller() throws Exception {
        User self = persistUser("notif-markall-self@example.com", UserTypeEnum.ELDER);
        Cookie selfCookie = loginCookieFor(self);

        seedNotification(self.getId(), Map.of("elderName", "A", "serviceName", "B", "bookingDate", "C"));
        notificationService.send("BOOKING_CANCELLED", Map.of("elderName", "A", "serviceName", "B", "bookingDate", "C"), self.getId());

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
