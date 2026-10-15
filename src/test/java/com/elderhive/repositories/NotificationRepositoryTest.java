package com.elderhive.repositories;

import com.elderhive.entities.Notification;
import com.elderhive.entities.User;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres — confirms V12's migration actually matches the
 * entity mapping (ddl-auto=validate would fail fast if not), the user_id FK's ON DELETE
 * CASCADE, JPA auditing, and both derived query methods against real data, not just that
 * the entity compiles.
 */
@SpringBootTest
@Transactional
class NotificationRepositoryTest {

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

    private Notification persistNotification(Long userId, NotificationTypeEnum type, boolean isRead) {
        return notificationRepository.save(Notification.builder()
                .userId(userId).type(type).title("Title").message("Message").isRead(isRead).build());
    }

    @Test
    void persistingAndReloadingANotificationPopulatesAuditFieldsAndEveryColumn() {
        User user = persistUser("notif-roundtrip@example.com");
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(user.getId()).type(NotificationTypeEnum.BOOKING).title("Booking confirmed")
                .message("Your booking is confirmed.").link("/bookings/1").isRead(false).build());
        entityManager.flush();
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(reloaded.getTitle()).isEqualTo("Booking confirmed");
        assertThat(reloaded.getMessage()).isEqualTo("Your booking is confirmed.");
        assertThat(reloaded.getLink()).isEqualTo("/bookings/1");
        assertThat(reloaded.isRead()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void linkIsGenuinelyNullable() {
        User user = persistUser("notif-no-link@example.com");
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(user.getId()).type(NotificationTypeEnum.SYSTEM).title("System notice")
                .message("No link here.").link(null).isRead(false).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(saved.getId()).orElseThrow().getLink()).isNull();
    }

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsOnlyThatUsersNotificationsNewestFirst() throws InterruptedException {
        User user = persistUser("notif-listing@example.com");
        User otherUser = persistUser("notif-other@example.com");
        persistNotification(otherUser.getId(), NotificationTypeEnum.SYSTEM, false);

        Notification first = persistNotification(user.getId(), NotificationTypeEnum.BOOKING, false);
        entityManager.flush();
        Thread.sleep(5); // ensure a distinct createdAt ordering, timestamps alone can tie within the same millisecond
        Notification second = persistNotification(user.getId(), NotificationTypeEnum.EMERGENCY, false);
        entityManager.flush();

        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Notification::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void countByUserIdAndIsReadFalseCountsOnlyUnreadNotificationsForThatUser() {
        User user = persistUser("notif-unread-count@example.com");
        persistNotification(user.getId(), NotificationTypeEnum.BOOKING, false);
        persistNotification(user.getId(), NotificationTypeEnum.EMERGENCY, false);
        persistNotification(user.getId(), NotificationTypeEnum.SYSTEM, true); // already read
        entityManager.flush();

        assertThat(notificationRepository.countByUserIdAndIsReadFalse(user.getId())).isEqualTo(2);
    }

    @Test
    void deletingTheReferencedUserCascadesToTheirNotifications() {
        User user = persistUser("notif-cascade@example.com");
        Notification notification = persistNotification(user.getId(), NotificationTypeEnum.SYSTEM, false);
        entityManager.flush();

        userRepository.delete(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(notification.getId())).isEmpty();
    }
}
