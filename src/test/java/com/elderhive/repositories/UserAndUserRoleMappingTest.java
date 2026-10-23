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

import java.util.List;

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

    // Backs AdminUserController's grant/revoke (Prompt 93).
    @Test
    void existsByUserIdAndUserTypeAndFindByUserIdAndUserTypeOnlyMatchTheExactCombination() {
        User user = persistUser("exists-by-combo@example.com", UserTypeEnum.FAMILY_MEMBER);
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build());

        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(user.getId(), UserTypeEnum.FAMILY_MEMBER)).isTrue();
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(user.getId(), UserTypeEnum.CARETAKER)).isFalse();
        assertThat(userRoleMappingRepository.findByUserIdAndUserType(user.getId(), UserTypeEnum.FAMILY_MEMBER)).isPresent();
        assertThat(userRoleMappingRepository.findByUserIdAndUserType(user.getId(), UserTypeEnum.CARETAKER)).isEmpty();
    }

    // Backs AdminUserController's listUsers response batching (Prompt 93).
    @Test
    void findByUserIdInReturnsMappingsForEveryRequestedUserInOneQuery() {
        User a = persistUser("batch-roles-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User b = persistUser("batch-roles-b@example.com", UserTypeEnum.CARETAKER);
        User c = persistUser("batch-roles-c@example.com", UserTypeEnum.ELDER);
        userRoleMappingRepository.save(UserRoleMapping.builder().userId(a.getId()).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build());
        userRoleMappingRepository.save(UserRoleMapping.builder().userId(b.getId()).userType(UserTypeEnum.CARETAKER).isPrimary(true).build());
        userRoleMappingRepository.save(UserRoleMapping.builder().userId(c.getId()).userType(UserTypeEnum.ELDER).isPrimary(true).build());

        assertThat(userRoleMappingRepository.findByUserIdIn(List.of(a.getId(), b.getId())))
                .extracting(UserRoleMapping::getUserId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }
}
