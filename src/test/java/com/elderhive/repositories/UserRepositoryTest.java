package com.elderhive.repositories;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres - only {@code countByUserTypeAndStatus} (Prompt 94)
 * needs its own coverage here; {@code findByEmail} is already exercised end-to-end by every
 * integration test's own login-cookie helper.
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
}
