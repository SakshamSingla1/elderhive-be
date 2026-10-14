package com.elderhive.security;

import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Enforces ElderHive's fine-grained admin RBAC (Role/Permission/RolePermission, Prompt 20)
 * from {@code @PreAuthorize} SpEL expressions as
 * {@code @adminPermissionGuard.has('SOME_PERMISSION_CODE')}.
 *
 * <p><b>Unlike ElderSphere's equivalent guard, which fails OPEN (returns {@code true}) for
 * every branch it can't fully resolve — a legacy-compat concession for admin accounts that
 * predate the Role/Permission system — this guard fails CLOSED everywhere.</b> There is
 * exactly one unconditional grant and everything else is a denial:
 * <ul>
 *   <li>{@code SUPER_ADMIN} (checked via {@code UserTypeEnum}, never via the Role table)
 *       always passes, regardless of {@code roleId}.</li>
 *   <li>No authenticated principal, or no matching {@code User} row -&gt; deny.</li>
 *   <li>Any non-{@code ADMIN}, non-{@code SUPER_ADMIN} caller -&gt; deny — this guard only
 *       ever grants for those two user types, never defers to "the surrounding
 *       {@code @PreAuthorize} clause will handle it" the way ElderSphere's does.</li>
 *   <li>{@code ADMIN} with no {@code roleId} assigned -&gt; deny.</li>
 *   <li>{@code roleId} pointing at a {@link Role} row that no longer exists -&gt; deny.</li>
 *   <li>Unknown permission code (no matching {@link Permission}) -&gt; deny.</li>
 *   <li>Permission code not actually granted to the caller's {@link Role} -&gt; deny.</li>
 * </ul>
 */
@Component("adminPermissionGuard")
@RequiredArgsConstructor
public class AdminPermissionGuard {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public boolean has(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return false;
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return false;
        }

        if (user.getUserType() == UserTypeEnum.SUPER_ADMIN) {
            return true;
        }

        if (user.getUserType() != UserTypeEnum.ADMIN) {
            return false;
        }

        Long roleId = user.getRoleId();
        if (roleId == null) {
            return false;
        }

        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return false;
        }

        Permission permission = permissionRepository.findByCode(permissionCode).orElse(null);
        if (permission == null) {
            return false;
        }

        return rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId());
    }
}
