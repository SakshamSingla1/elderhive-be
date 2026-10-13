package com.elderhive.repositories;

import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres (docker-compose.yml) — confirms the two tables,
 * their unique constraints, and the FK relationship actually work together, not just
 * that the entities compile and Hibernate's ddl-auto=validate accepts the mapping.
 */
@SpringBootTest
@Transactional
class UserAndUserRoleMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleMappingRepository userRoleMappingRepository;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .fullName("Test User")
                .userType(userType)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    @Test
    void savesUserAndItsPrimaryRoleMapping() {
        User user = persistUser("family@example.com", UserTypeEnum.FAMILY_MEMBER);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();

        UserRoleMapping mapping = userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId())
                .userType(UserTypeEnum.FAMILY_MEMBER)
                .isPrimary(true)
                .build());

        assertThat(mapping.getId()).isNotNull();
        assertThat(mapping.isPrimary()).isTrue();
        assertThat(mapping.getGrantedByUserId()).isNull();
    }

    @Test
    void aUserCanHoldMultipleRolesSimultaneously() {
        User user = persistUser("multi@example.com", UserTypeEnum.FAMILY_MEMBER);

        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build());
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.CARETAKER).isPrimary(false).build());

        assertThat(userRoleMappingRepository.findAll())
                .filteredOn(m -> m.getUserId().equals(user.getId()))
                .hasSize(2);
    }

    @Test
    void rejectsDuplicateEmail() {
        persistUser("dup@example.com", UserTypeEnum.ELDER);

        assertThatThrownBy(() -> {
            userRepository.save(User.builder()
                    .email("dup@example.com")
                    .passwordHash("hashed")
                    .userType(UserTypeEnum.ELDER)
                    .status(UserStatusEnum.ACTIVE)
                    .build());
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateUserIdAndUserTypeOnRoleMapping() {
        User user = persistUser("dup-role@example.com", UserTypeEnum.CARETAKER);
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.CARETAKER).isPrimary(true).build());

        assertThatThrownBy(() -> {
            userRoleMappingRepository.save(UserRoleMapping.builder()
                    .userId(user.getId()).userType(UserTypeEnum.CARETAKER).isPrimary(false).build());
            userRoleMappingRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
