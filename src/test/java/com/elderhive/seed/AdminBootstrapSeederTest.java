package com.elderhive.seed;

import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres (docker-compose.yml). Note the seeder already ran
 * once automatically as a CommandLineRunner during this test class's own context startup
 * (Spring Boot runs every CommandLineRunner bean once per ApplicationContext refresh) —
 * the explicit run() calls below are IN ADDITION to that, which is exactly the point:
 * proving a seeder genuinely meant to run on every boot stays idempotent no matter how
 * many times it's actually invoked, not just the first.
 */
@SpringBootTest
@Transactional
class AdminBootstrapSeederTest {

    private static final List<String> EXPECTED_PERMISSION_CODES = List.of(
            "USER_MANAGE", "ROLE_MANAGE", "CONFIG_MANAGE", "NAVLINK_MANAGE", "THEME_MANAGE",
            "SERVICE_MANAGE", "BOOKING_MANAGE", "CARETAKER_VERIFY", "EMERGENCY_DISPATCH", "DASHBOARD_VIEW",
            "LANDING_MANAGE", "CONTACT_US_MANAGE");

    private static final Set<String> SUPER_ADMIN_ONLY_CODES = Set.of("ROLE_MANAGE", "CONFIG_MANAGE");

    @Autowired
    private AdminBootstrapSeeder seeder;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Test
    void seedsTheFullAuthoritativePermissionCatalogExactlyOnce() {
        seeder.run();
        seeder.run();

        List<Permission> matching = EXPECTED_PERMISSION_CODES.stream()
                .map(code -> permissionRepository.findByCode(code).orElse(null))
                .toList();

        assertThat(matching).doesNotContainNull();
        for (String code : EXPECTED_PERMISSION_CODES) {
            assertThat(permissionRepository.findAll().stream().filter(p -> p.getCode().equals(code)).count())
                    .as("exactly one Permission row for code %s", code)
                    .isEqualTo(1);
        }
    }

    @Test
    void superAdminRoleHasEveryPermissionAndAdminRoleExcludesTheReservedTwo() {
        seeder.run();

        Role superAdmin = roleRepository.findByName("Super Admin").orElseThrow();
        Role admin = roleRepository.findByName("Admin").orElseThrow();

        Set<String> superAdminCodes = rolePermissionRepository.findByRoleId(superAdmin.getId()).stream()
                .map(rp -> permissionRepository.findById(rp.getPermissionId()).orElseThrow().getCode())
                .collect(Collectors.toSet());
        Set<String> adminCodes = rolePermissionRepository.findByRoleId(admin.getId()).stream()
                .map(rp -> permissionRepository.findById(rp.getPermissionId()).orElseThrow().getCode())
                .collect(Collectors.toSet());

        assertThat(superAdminCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_PERMISSION_CODES);

        assertThat(adminCodes).doesNotContainAnyElementsOf(SUPER_ADMIN_ONLY_CODES);
        assertThat(adminCodes).hasSize(EXPECTED_PERMISSION_CODES.size() - SUPER_ADMIN_ONLY_CODES.size());
        EXPECTED_PERMISSION_CODES.stream()
                .filter(code -> !SUPER_ADMIN_ONLY_CODES.contains(code))
                .forEach(code -> assertThat(adminCodes).contains(code));
    }

    @Test
    void runningTwiceDoesNotDuplicateAnyRows() {
        seeder.run();
        long permissionsAfterFirst = permissionRepository.count();
        long rolesAfterFirst = roleRepository.count();
        long rolePermissionsAfterFirst = rolePermissionRepository.count();

        seeder.run();
        seeder.run();

        assertThat(permissionRepository.count()).isEqualTo(permissionsAfterFirst);
        assertThat(roleRepository.count()).isEqualTo(rolesAfterFirst);
        assertThat(rolePermissionRepository.count()).isEqualTo(rolePermissionsAfterFirst);
    }
}
