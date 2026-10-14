package com.elderhive.seed;

import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.RolePermission;
import com.elderhive.entities.User;
import com.elderhive.enums.RoleStatusEnum;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Seeds ElderHive's admin RBAC catalog (Permission/Role/RolePermission, Prompts 20/23) on
 * every boot — idempotent, unconditional, the catalog itself isn't sensitive — then
 * optionally assigns the "Super Admin" role to one account named by
 * {@code app.admin.bootstrap-email}, mirroring the DSA tracker's AdminBootstrapSeeder: the
 * only way to create the first fine-grained admin, since there's no self-service "become
 * admin" flow.
 *
 * <p>PERMISSIONS is the authoritative catalog — later phases' {@code @PreAuthorize}
 * checks (via {@link com.elderhive.security.AdminPermissionGuard}) reference these exact
 * code strings, so don't rename or remove an entry here without updating every guard
 * expression that references it.
 *
 * <p>Deliberately safe to leave {@code app.admin.bootstrap-email} configured permanently
 * as a "break glass" mechanism, same reasoning as the DSA tracker's version:
 * <ul>
 *   <li>unset/blank email -&gt; no-op</li>
 *   <li>named account doesn't exist yet -&gt; no-op (never creates a user)</li>
 *   <li>named account already has a roleId -&gt; no-op (never clobbers an existing
 *       assignment, manual or otherwise, so demoting/reassigning an admin via the admin
 *       UI survives a redeploy/restart)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapSeeder implements CommandLineRunner {

    private record PermissionDef(String code, String description) {
    }

    private static final List<PermissionDef> PERMISSIONS = List.of(
            new PermissionDef("USER_MANAGE", "View users, assign roles, enable/disable accounts"),
            new PermissionDef("ROLE_MANAGE", "Create and edit roles, assign permissions to roles"),
            new PermissionDef("CONFIG_MANAGE", "Read and write the generic meta-configuration store"),
            new PermissionDef("NAVLINK_MANAGE", "Create, edit, reorder, and delete navigation links"),
            new PermissionDef("THEME_MANAGE", "Create, edit, and publish color themes"),
            new PermissionDef("SERVICE_MANAGE", "Create, edit, and delete bookable service offerings"),
            new PermissionDef("BOOKING_MANAGE", "View and manage bookings across all users"),
            new PermissionDef("CARETAKER_VERIFY", "Review and verify caretaker profile applications"),
            new PermissionDef("EMERGENCY_DISPATCH", "View and act on emergency SOS dispatches"),
            new PermissionDef("DASHBOARD_VIEW", "View the admin stats dashboard")
    );

    private static final String SUPER_ADMIN_ROLE_NAME = "Super Admin";
    private static final String ADMIN_ROLE_NAME = "Admin";

    // Stay Super-Admin-only - every other permission is also granted to the curated Admin role.
    private static final Set<String> SUPER_ADMIN_ONLY_CODES = Set.of("ROLE_MANAGE", "CONFIG_MANAGE");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapEmail;

    @Override
    @Transactional
    public void run(String... args) {
        List<Permission> allPermissions = ensurePermissions();

        List<Permission> adminPermissions = allPermissions.stream()
                .filter(p -> !SUPER_ADMIN_ONLY_CODES.contains(p.getCode()))
                .toList();

        Role superAdmin = ensureRole(SUPER_ADMIN_ROLE_NAME, "Full access to every admin screen");
        ensureRolePermissions(superAdmin, allPermissions);

        Role admin = ensureRole(ADMIN_ROLE_NAME, "Every admin capability except role and platform-config management");
        ensureRolePermissions(admin, adminPermissions);

        assignBootstrapAdminIfConfigured(superAdmin);
    }

    private List<Permission> ensurePermissions() {
        List<Permission> result = new ArrayList<>();
        for (PermissionDef def : PERMISSIONS) {
            Permission permission = permissionRepository.findByCode(def.code())
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .code(def.code())
                            .description(def.description())
                            .build()));
            result.add(permission);
        }
        return result;
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(name)
                        .description(description)
                        .status(RoleStatusEnum.ACTIVE)
                        .build()));
    }

    private void ensureRolePermissions(Role role, List<Permission> permissions) {
        // Checked individually rather than "replace the whole set" so a role's manually
        // revoked permission (via the admin UI, once that exists) never silently comes
        // back just because the seeder ran again — only ever adds, never removes.
        for (Permission permission : permissions) {
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
                rolePermissionRepository.save(RolePermission.builder()
                        .roleId(role.getId())
                        .permissionId(permission.getId())
                        .build());
            }
        }
    }

    private void assignBootstrapAdminIfConfigured(Role superAdmin) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        String email = bootstrapEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getRoleId() != null) {
            return;
        }
        user.setRoleId(superAdmin.getId());
        userRepository.save(user);
    }
}
