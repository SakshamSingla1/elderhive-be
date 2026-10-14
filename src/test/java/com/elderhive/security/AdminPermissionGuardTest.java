package com.elderhive.security;

import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves AdminPermissionGuard denies by default: every branch it can't fully resolve to
 * an explicit grant must return false, not true (the opposite of ElderSphere's fail-open
 * equivalent). Each failure-branch test also asserts which repositories were NEVER
 * consulted, to prove the guard short-circuits rather than coincidentally returning false
 * after still reaching further than it should.
 */
@ExtendWith(MockitoExtension.class)
class AdminPermissionGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private AdminPermissionGuard guard;

    private void authenticateAs(String email) {
        Authentication authentication = new TestingAuthenticationToken(email, "n/a");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User admin(Long roleId) {
        return User.builder()
                .id(1L)
                .email("admin@example.com")
                .userType(UserTypeEnum.ADMIN)
                .roleId(roleId)
                .build();
    }

    @Test
    void deniesWhenThereIsNoAuthenticatedPrincipal() {
        SecurityContextHolder.clearContext();

        assertThat(guard.has("USER_MANAGE")).isFalse();

        verifyNoInteractions(userRepository, roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesForAnonymousAuthentication() {
        Authentication anonymous = new TestingAuthenticationToken("anonymousUser", "n/a");
        anonymous.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThat(guard.has("USER_MANAGE")).isFalse();

        verifyNoInteractions(userRepository, roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesWhenNoUserRowMatchesTheAuthenticatedEmail() {
        authenticateAs("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThat(guard.has("USER_MANAGE")).isFalse();

        verifyNoInteractions(roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesForANonAdminNonSuperAdminCaller() {
        authenticateAs("elder@example.com");
        User elder = User.builder().id(2L).email("elder@example.com").userType(UserTypeEnum.ELDER).build();
        when(userRepository.findByEmail("elder@example.com")).thenReturn(Optional.of(elder));

        assertThat(guard.has("USER_MANAGE")).isFalse();

        // Never even looks at roleId for a non-ADMIN, non-SUPER_ADMIN caller.
        verifyNoInteractions(roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesForAnAdminWithNoRoleIdAssigned() {
        authenticateAs("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin(null)));

        assertThat(guard.has("USER_MANAGE")).isFalse();

        verifyNoInteractions(roleRepository, permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesWhenTheAssignedRoleNoLongerExists() {
        authenticateAs("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin(10L)));
        when(roleRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(guard.has("USER_MANAGE")).isFalse();

        verifyNoInteractions(permissionRepository, rolePermissionRepository);
    }

    @Test
    void deniesWhenThePermissionCodeDoesNotExist() {
        authenticateAs("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin(10L)));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(Role.builder().id(10L).name("SUPPORT").build()));
        when(permissionRepository.findByCode("NO_SUCH_PERMISSION")).thenReturn(Optional.empty());

        assertThat(guard.has("NO_SUCH_PERMISSION")).isFalse();

        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void deniesWhenThePermissionIsNotAssignedToTheRole() {
        authenticateAs("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin(10L)));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(Role.builder().id(10L).name("SUPPORT").build()));
        when(permissionRepository.findByCode("USER_MANAGE"))
                .thenReturn(Optional.of(Permission.builder().id(99L).code("USER_MANAGE").build()));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(10L, 99L)).thenReturn(false);

        assertThat(guard.has("USER_MANAGE")).isFalse();
    }

    @Test
    void grantsWhenTheRoleActuallyHasThePermissionAssigned() {
        authenticateAs("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin(10L)));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(Role.builder().id(10L).name("SUPPORT").build()));
        when(permissionRepository.findByCode("USER_MANAGE"))
                .thenReturn(Optional.of(Permission.builder().id(99L).code("USER_MANAGE").build()));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(10L, 99L)).thenReturn(true);

        assertThat(guard.has("USER_MANAGE")).isTrue();
    }

    @Test
    void superAdminAlwaysPassesRegardlessOfRoleId() {
        authenticateAs("super@example.com");
        User superAdmin = User.builder()
                .id(3L)
                .email("super@example.com")
                .userType(UserTypeEnum.SUPER_ADMIN)
                .roleId(null)
                .build();
        when(userRepository.findByEmail("super@example.com")).thenReturn(Optional.of(superAdmin));

        assertThat(guard.has("ANYTHING_AT_ALL")).isTrue();

        // Checked via UserTypeEnum alone - never consults the Role table for SUPER_ADMIN.
        verifyNoInteractions(roleRepository, permissionRepository, rolePermissionRepository);
    }
}
