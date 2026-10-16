package com.elderhive.repositories;

import com.elderhive.entities.PushSubscription;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V14's migration actually matches the
 * entity mapping (ddl-auto=validate would fail fast if not), the user_id FK's ON DELETE
 * CASCADE, the endpoint unique constraint, and JPA auditing.
 */
@SpringBootTest
@Transactional
class PushSubscriptionRepositoryTest {

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

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
    void persistingAndReloadingASubscriptionPopulatesAuditFieldsAndEveryColumn() {
        User user = persistUser("push-roundtrip@example.com");

        PushSubscription saved = pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId())
                .endpoint("https://push.example.com/ep/roundtrip")
                .p256dhKey("dummy-p256dh")
                .authKey("dummy-auth")
                .build());
        entityManager.flush();
        entityManager.clear();

        PushSubscription reloaded = pushSubscriptionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getUserId()).isEqualTo(user.getId());
        assertThat(reloaded.getEndpoint()).isEqualTo("https://push.example.com/ep/roundtrip");
        assertThat(reloaded.getP256dhKey()).isEqualTo("dummy-p256dh");
        assertThat(reloaded.getAuthKey()).isEqualTo("dummy-auth");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void theSameEndpointCannotBeRegisteredTwice() {
        User user = persistUser("push-unique@example.com");
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/dup")
                .p256dhKey("key1").authKey("auth1").build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            pushSubscriptionRepository.save(PushSubscription.builder()
                    .userId(user.getId()).endpoint("https://push.example.com/ep/dup")
                    .p256dhKey("key2").authKey("auth2").build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersSubscriptions() {
        User user = persistUser("push-find-by-user@example.com");
        User otherUser = persistUser("push-find-other-user@example.com");
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/a")
                .p256dhKey("key").authKey("auth").build());
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/b")
                .p256dhKey("key").authKey("auth").build());
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(otherUser.getId()).endpoint("https://push.example.com/ep/c")
                .p256dhKey("key").authKey("auth").build());
        entityManager.flush();

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(user.getId());

        assertThat(subscriptions).hasSize(2);
        assertThat(subscriptions).extracting(PushSubscription::getEndpoint)
                .containsExactlyInAnyOrder("https://push.example.com/ep/a", "https://push.example.com/ep/b");
    }

    @Test
    void deleteByUserIdAndEndpointRemovesOnlyThatOneRow() {
        User user = persistUser("push-delete-one@example.com");
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/keep")
                .p256dhKey("key").authKey("auth").build());
        pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/remove")
                .p256dhKey("key").authKey("auth").build());
        entityManager.flush();

        pushSubscriptionRepository.deleteByUserIdAndEndpoint(user.getId(), "https://push.example.com/ep/remove");
        entityManager.flush();
        entityManager.clear();

        List<PushSubscription> remaining = pushSubscriptionRepository.findByUserId(user.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getEndpoint()).isEqualTo("https://push.example.com/ep/keep");
    }

    @Test
    void deletingTheOwningUserCascadesToTheirPushSubscriptions() {
        User user = persistUser("push-cascade@example.com");
        PushSubscription subscription = pushSubscriptionRepository.save(PushSubscription.builder()
                .userId(user.getId()).endpoint("https://push.example.com/ep/cascade")
                .p256dhKey("key").authKey("auth").build());
        entityManager.flush();

        userRepository.delete(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(pushSubscriptionRepository.findById(subscription.getId())).isEmpty();
    }
}
