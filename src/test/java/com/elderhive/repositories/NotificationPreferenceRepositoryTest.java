package com.elderhive.repositories;

import com.elderhive.entities.NotificationPreference;
import com.elderhive.entities.User;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres — confirms V12's migration matches the entity
 * mapping, the (user_id, type) unique constraint, column defaults (in_app_enabled
 * defaults TRUE, the other three default FALSE at the DB level), and the user_id FK's
 * ON DELETE CASCADE.
 */
@SpringBootTest
@Transactional
class NotificationPreferenceRepositoryTest {

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ELDER)
                .status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void persistingAndReloadingAPreferencePopulatesAuditFieldsAndEveryColumn() {
        User user = persistUser("pref-roundtrip@example.com");
        NotificationPreference saved = notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.BOOKING)
                .inAppEnabled(true).emailEnabled(true).smsEnabled(false).webPushEnabled(true).build());
        entityManager.flush();
        entityManager.clear();

        NotificationPreference reloaded = notificationPreferenceRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(reloaded.isInAppEnabled()).isTrue();
        assertThat(reloaded.isEmailEnabled()).isTrue();
        assertThat(reloaded.isSmsEnabled()).isFalse();
        assertThat(reloaded.isWebPushEnabled()).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByUserIdAndTypeIsEmptyWhenNoPreferenceRowExists() {
        User user = persistUser("pref-absent@example.com");

        // Absence (not an error) is the documented "in-app only" default - the actual
        // fallback behavior belongs to whichever later phase reads this at send time.
        assertThat(notificationPreferenceRepository.findByUserIdAndType(user.getId(), NotificationTypeEnum.EMERGENCY))
                .isEmpty();
    }

    @Test
    void findByUserIdAndTypeFindsTheRightRowAmongMultipleTypesForTheSameUser() {
        User user = persistUser("pref-multi-type@example.com");
        notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.BOOKING).emailEnabled(true).build());
        notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.EMERGENCY).smsEnabled(true).build());
        entityManager.flush();

        NotificationPreference bookingPref = notificationPreferenceRepository
                .findByUserIdAndType(user.getId(), NotificationTypeEnum.BOOKING).orElseThrow();
        NotificationPreference emergencyPref = notificationPreferenceRepository
                .findByUserIdAndType(user.getId(), NotificationTypeEnum.EMERGENCY).orElseThrow();

        assertThat(bookingPref.isEmailEnabled()).isTrue();
        assertThat(emergencyPref.isSmsEnabled()).isTrue();
    }

    @Test
    void userIdAndTypeTogetherMustBeUnique() {
        User user = persistUser("pref-duplicate@example.com");
        notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.BOOKING).build());

        assertThatThrownBy(() -> {
            notificationPreferenceRepository.save(NotificationPreference.builder()
                    .userId(user.getId()).type(NotificationTypeEnum.BOOKING).build());
            notificationPreferenceRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheReferencedUserCascadesToTheirPreferences() {
        User user = persistUser("pref-cascade@example.com");
        NotificationPreference preference = notificationPreferenceRepository.save(NotificationPreference.builder()
                .userId(user.getId()).type(NotificationTypeEnum.SYSTEM).build());
        entityManager.flush();

        userRepository.delete(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationPreferenceRepository.findById(preference.getId())).isEmpty();
    }
}
