package com.elderhive.repositories;

import com.elderhive.entities.User;
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
 * Runs against the real local Postgres - {@code countByUserTypeAndStatus} (Prompt 94) and
 * {@code findByCriteria} (Prompt 93) need their own coverage here; {@code findByEmail} is
 * already exercised end-to-end by every integration test's own login-cookie helper.
 */
@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType, UserStatusEnum status) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(status).build());
    }

    private User persistUser(String email, String fullName, UserTypeEnum userType, UserStatusEnum status) {
        return userRepository.save(User.builder()
                .email(email).fullName(fullName).passwordHash("hashed").userType(userType).status(status).build());
    }

    @Test
    void countByUserTypeAndStatusCountsOnlyThatExactCombination() {
        // Delta-based (not an absolute count) - this table is shared across every other
        // test in the suite, same precedent AdminDashboardControllerIntegrationTest's own
        // dashboard assertions use.
        long baselineActiveCaretaker = userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE);
        long baselineInactiveCaretaker = userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.INACTIVE);
        long baselineActiveFamily = userRepository.countByUserTypeAndStatus(UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE);

        persistUser("user-countrole-active-caretaker@example.com", UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE);
        persistUser("user-countrole-inactive-caretaker@example.com", UserTypeEnum.CARETAKER, UserStatusEnum.INACTIVE);
        persistUser("user-countrole-active-family@example.com", UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE);
        entityManager.flush();

        assertThat(userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE)).isEqualTo(baselineActiveCaretaker + 1);
        assertThat(userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.INACTIVE)).isEqualTo(baselineInactiveCaretaker + 1);
        assertThat(userRepository.countByUserTypeAndStatus(UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE)).isEqualTo(baselineActiveFamily + 1);
    }

    @Test
    void findByCriteriaMatchesEmailOrFullNameCaseInsensitively() {
        // A sufficiently unique pattern that can't collide with any pre-existing dev-DB
        // data, so this test asserts absolute content rather than needing the
        // baseline-delta pattern countByUserTypeAndStatus's own test above uses for a
        // genuinely global/unscoped count.
        persistUser("findcriteria-zyx@example.com", "Zyx Qmbl", UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE);
        persistUser("other-zyx@example.com", "Someone Else", UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE);
        entityManager.flush();

        Page<User> byEmail = userRepository.findByCriteria("%findcriteria-zyx%", null, null, PageRequest.of(0, 10));
        Page<User> byFullName = userRepository.findByCriteria("%qmbl%", null, null, PageRequest.of(0, 10));

        assertThat(byEmail.getContent()).extracting(User::getEmail).containsExactly("findcriteria-zyx@example.com");
        assertThat(byFullName.getContent()).extracting(User::getEmail).containsExactly("findcriteria-zyx@example.com");
    }

    @Test
    void findByCriteriaAppliesUserTypeAndStatusFiltersTogetherWithSearch() {
        persistUser("findcriteria-combo-a@example.com", "Combo Match", UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE);
        persistUser("findcriteria-combo-b@example.com", "Combo Match", UserTypeEnum.CARETAKER, UserStatusEnum.INACTIVE);
        persistUser("findcriteria-combo-c@example.com", "Combo Match", UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE);
        entityManager.flush();

        Page<User> result = userRepository.findByCriteria("%findcriteria-combo%", UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getEmail).containsExactly("findcriteria-combo-a@example.com");
    }

    @Test
    void findByCriteriaReturnsEverythingWhenEveryPredicateIsNull() {
        long baselineTotal = userRepository.count();
        persistUser("findcriteria-null-filter@example.com", UserTypeEnum.ELDER, UserStatusEnum.ACTIVE);
        entityManager.flush();

        Page<User> result = userRepository.findByCriteria(null, null, null, PageRequest.of(0, Math.max(1, (int) baselineTotal + 5)));

        assertThat(result.getTotalElements()).isEqualTo(baselineTotal + 1);
    }
}
