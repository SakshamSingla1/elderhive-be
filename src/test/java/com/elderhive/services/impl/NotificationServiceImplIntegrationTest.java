package com.elderhive.services.impl;

import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationPreference;
import com.elderhive.entities.NotificationTemplate;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationDeliveryLogRepository;
import com.elderhive.repositories.NotificationPreferenceRepository;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real NotificationService bean - real TemplateRenderer, real repositories, the
 * real WebPushSenderService bean (safe here: with no PushSubscription rows, its own
 * fail-soft no-subscriptions path is a genuine no-op, never a real network call) and a real
 * SimpMessagingTemplate broadcast - against Postgres, proving the whole orchestration wires
 * together end to end, not just NotificationServiceImplTest's mocked branch logic.
 */
@SpringBootTest
@Transactional
class NotificationServiceImplIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryLogRepository notificationDeliveryLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build());
    }

    private NotificationTemplate persistTemplate(String templateKey, NotificationTypeEnum type,
                                                  boolean sms, boolean email, boolean webPush) {
        return notificationTemplateRepository.save(NotificationTemplate.builder()
                .templateKey(templateKey).type(type)
                .subject("Hi {{name}}").messageBody("Hello {{name}}, this is a test.")
                .isInApp(true).isSms(sms).isEmail(email).isWebPush(webPush)
                .build());
    }

    @Test
    void sendWithNoPreferenceRowWritesOnlyTheInAppNotificationAndDeliveryLog() throws GenericException {
        User user = persistUser("notify-no-pref@example.com");
        persistTemplate("integration.no-pref", NotificationTypeEnum.SYSTEM, true, true, true);
        entityManager.flush();

        notificationService.send("integration.no-pref", Map.of("name", "Robert"), user.getId());
        entityManager.flush();
        entityManager.clear();

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getTitle()).isEqualTo("Hi Robert");
        assertThat(notification.getMessage()).isEqualTo("Hello Robert, this is a test.");
        assertThat(notification.getType()).isEqualTo(NotificationTypeEnum.SYSTEM);

        assertThat(notificationDeliveryLogRepository.findByNotificationId(notification.getId()))
                .extracting(log -> log.getChannel())
                .containsExactly(NotificationChannelEnum.IN_APP);
    }

    @Test
    void sendWithSmsAndEmailEnabledPreferenceWritesAllThreeApplicableChannels() throws GenericException {
        User user = persistUser("notify-sms-email@example.com");
        persistTemplate("integration.sms-email", NotificationTypeEnum.BOOKING, true, true, false);
        notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.BOOKING)
                .inAppEnabled(true).smsEnabled(true).emailEnabled(true).webPushEnabled(false).build());
        entityManager.flush();

        notificationService.send("integration.sms-email", Map.of("name", "Jane"), user.getId());
        entityManager.flush();
        entityManager.clear();

        Notification notification = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0);

        assertThat(notificationDeliveryLogRepository.findByNotificationId(notification.getId()))
                .extracting(log -> log.getChannel())
                .containsExactlyInAnyOrder(NotificationChannelEnum.IN_APP, NotificationChannelEnum.SIMULATED_SMS,
                        NotificationChannelEnum.SIMULATED_EMAIL);
    }

    @Test
    void sendWithWebPushEnabledButNoActiveSubscriptionWritesNoWebPushLogAndDoesNotThrow() throws GenericException {
        User user = persistUser("notify-webpush-no-sub@example.com");
        persistTemplate("integration.webpush-no-sub", NotificationTypeEnum.EMERGENCY, false, false, true);
        notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.EMERGENCY).webPushEnabled(true).build());
        entityManager.flush();

        notificationService.send("integration.webpush-no-sub", Map.of("name", "Sam"), user.getId());
        entityManager.flush();
        entityManager.clear();

        Notification notification = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0);

        assertThat(notificationDeliveryLogRepository.findByNotificationId(notification.getId()))
                .extracting(log -> log.getChannel())
                .containsExactly(NotificationChannelEnum.IN_APP);
    }

    @Test
    void sendForANonExistentUserThrowsUserNotFoundAndWritesNothing() {
        persistTemplate("integration.no-user", NotificationTypeEnum.SYSTEM, false, false, false);
        entityManager.flush();

        assertThatThrownBy(() -> notificationService.send("integration.no-user", Map.of("name", "X"), 999999999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);
    }

    @Test
    void sendForAnUnregisteredTemplateKeyThrowsNotificationTemplateNotFound() {
        User user = persistUser("notify-no-template@example.com");
        entityManager.flush();

        assertThatThrownBy(() -> notificationService.send("integration.does-not-exist", Map.of(), user.getId()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND);
    }
}
