package com.elderhive.seed;

import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * app.admin.bootstrap-email is overridden here, so this loads a SEPARATE Spring context
 * from every other @SpringBootTest in this module (different property set) — the seeder
 * still runs once automatically on that context's own startup, at which point the test
 * users below don't exist yet, so that automatic run is a no-op for both. The explicit
 * seeder.run() calls in each test are what exercise the assignment logic for real, on a
 * "later boot" once the account in question actually exists.
 */
@SpringBootTest
@TestPropertySource(properties = "app.admin.bootstrap-email=bootstrap-test@example.com")
@Transactional
class AdminBootstrapSeederBootstrapEmailTest {

    @Autowired
    private AdminBootstrapSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User persistAdmin(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .userType(UserTypeEnum.ADMIN)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    @Test
    void assignsSuperAdminRoleToTheConfiguredAccountOnceItExistsWithNoRoleIdYet() {
        User user = persistAdmin("bootstrap-test@example.com");
        assertThat(user.getRoleId()).isNull();

        seeder.run();

        Role superAdmin = roleRepository.findByName("Super Admin").orElseThrow();
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getRoleId()).isEqualTo(superAdmin.getId());
    }

    @Test
    void neverTouchesAnAlreadyManuallyAssignedRole() {
        seeder.run(); // ensures a Role catalog (and specifically some non-Super-Admin role) exists
        Role manuallyAssignedRole = roleRepository.findByName("Admin").orElseThrow();

        User user = persistAdmin("bootstrap-test@example.com");
        user.setRoleId(manuallyAssignedRole.getId());
        userRepository.save(user);

        seeder.run();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getRoleId()).isEqualTo(manuallyAssignedRole.getId());
    }

    @Test
    void isANoOpWhenTheConfiguredAccountDoesNotExist() {
        // No user with this email exists at all - must not create one.
        seeder.run();

        assertThat(userRepository.findByEmail("bootstrap-test@example.com")).isEmpty();
    }
}
