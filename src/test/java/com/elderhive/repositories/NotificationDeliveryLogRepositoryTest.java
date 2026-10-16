package com.elderhive.repositories;

import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationDeliveryLog;
import com.elderhive.entities.User;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationDeliveryStatusEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres — confirms V13's migration actually matches the
 * entity mapping (ddl-auto=validate would fail fast if not), the notification_id FK's ON
 * DELETE CASCADE, JPA auditing, and findByNotificationId against real data.
 */
@SpringBootTest
@Transactional
class NotificationDeliveryLogRepositoryTest {

    @Autowired
    private NotificationDeliveryLogRepository notificationDeliveryLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ELDER)
                .status(UserStatusEnum.ACTIVE).build());
    }

    private Notification persistNotification(Long userId) {
        return notificationRepository.save(Notification.builder()
                .userId(userId).type(NotificationTypeEnum.BOOKING).title("Booking confirmed")
                .message("Your booking is confirmed.").isRead(false).build());
    }

    @Test
    void persistingAndReloadingADeliveryLogPopulatesAuditFieldsAndEveryColumn() {
        User user = persistUser("delivery-roundtrip@example.com");
        Notification notification = persistNotification(user.getId());

        NotificationDeliveryLog saved = notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId())
                .channel(NotificationChannelEnum.IN_APP)
                .status(NotificationDeliveryStatusEnum.SENT)
                .subject("Booking confirmed")
                .body("Your booking is confirmed.")
                .build());
        entityManager.flush();
        entityManager.clear();

        NotificationDeliveryLog reloaded = notificationDeliveryLogRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getNotificationId()).isEqualTo(notification.getId());
        assertThat(reloaded.getChannel()).isEqualTo(NotificationChannelEnum.IN_APP);
        assertThat(reloaded.getStatus()).isEqualTo(NotificationDeliveryStatusEnum.SENT);
        assertThat(reloaded.getSubject()).isEqualTo("Booking confirmed");
        assertThat(reloaded.getBody()).isEqualTo("Your booking is confirmed.");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aRetroactiveTemplateEditNeverChangesAnAlreadyWrittenDeliveryLogRow() {
        // There's no NotificationTemplate FK here at all - subject/body are plain copied
        // strings, not a live join - so this is really just confirming the row holds
        // whatever was written and nothing re-derives it on read.
        User user = persistUser("delivery-frozen@example.com");
        Notification notification = persistNotification(user.getId());
        NotificationDeliveryLog saved = notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId())
                .channel(NotificationChannelEnum.SIMULATED_EMAIL)
                .status(NotificationDeliveryStatusEnum.SENT)
                .subject("Original rendered subject")
                .body("Original rendered body")
                .build());
        entityManager.flush();
        entityManager.clear();

        NotificationDeliveryLog reloaded = notificationDeliveryLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSubject()).isEqualTo("Original rendered subject");
        assertThat(reloaded.getBody()).isEqualTo("Original rendered body");
    }

    @Test
    void findByNotificationIdReturnsOneRowPerChannelForThatNotificationOnly() {
        User user = persistUser("delivery-multi-channel@example.com");
        Notification notification = persistNotification(user.getId());
        Notification otherNotification = persistNotification(user.getId());

        notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId()).channel(NotificationChannelEnum.IN_APP)
                .status(NotificationDeliveryStatusEnum.SENT).build());
        notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId()).channel(NotificationChannelEnum.WEB_PUSH)
                .status(NotificationDeliveryStatusEnum.FAILED).build());
        notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(otherNotification.getId()).channel(NotificationChannelEnum.IN_APP)
                .status(NotificationDeliveryStatusEnum.SENT).build());
        entityManager.flush();

        List<NotificationDeliveryLog> logs = notificationDeliveryLogRepository.findByNotificationId(notification.getId());

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(NotificationDeliveryLog::getChannel)
                .containsExactlyInAnyOrder(NotificationChannelEnum.IN_APP, NotificationChannelEnum.WEB_PUSH);
    }

    @Test
    void deletingTheReferencedNotificationCascadesToItsDeliveryLogs() {
        User user = persistUser("delivery-cascade@example.com");
        Notification notification = persistNotification(user.getId());
        NotificationDeliveryLog log = notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId()).channel(NotificationChannelEnum.IN_APP)
                .status(NotificationDeliveryStatusEnum.SENT).build());
        entityManager.flush();

        notificationRepository.delete(notification);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationDeliveryLogRepository.findById(log.getId())).isEmpty();
    }
}
