package com.elderhive.services.impl;

import com.elderhive.dtos.NavLink.NavLinkResponse;
import com.elderhive.entities.NavLink;
import com.elderhive.entities.User;
import com.elderhive.enums.NavLinkStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.NavLinkRepository;
import com.elderhive.security.AdminPermissionGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves getMine()'s three-way branch exactly as documented in NavLinkServiceImpl: non-
 * ADMIN/non-SUPER_ADMIN gets nothing (their nav is FE-hardcoded, not DB-driven), ADMIN
 * gets superAdminOnly rows excluded and requiredPermission enforced via the guard, and
 * SUPER_ADMIN gets everything unfiltered without ever touching the guard.
 */
@ExtendWith(MockitoExtension.class)
class NavLinkServiceImplTest {

    @Mock
    private NavLinkRepository navLinkRepository;

    @Mock
    private AdminPermissionGuard adminPermissionGuard;

    @InjectMocks
    private NavLinkServiceImpl navLinkService;

    private NavLink link(String name, String requiredPermission, boolean superAdminOnly) {
        return NavLink.builder()
                .id((long) name.hashCode())
                .name(name)
                .path("/" + name)
                .navIndex(0)
                .requiredPermission(requiredPermission)
                .superAdminOnly(superAdminOnly)
                .status(NavLinkStatusEnum.ACTIVE)
                .build();
    }

    @Test
    void returnsAnEmptyListForANonAdminNonSuperAdminCaller() {
        User elder = User.builder().id(1L).userType(UserTypeEnum.ELDER).build();

        List<NavLinkResponse> result = navLinkService.getMine(elder);

        assertThat(result).isEmpty();
        verifyNoInteractions(navLinkRepository, adminPermissionGuard);
    }

    @Test
    void adminSeesUngatedAndGrantedPermissionLinksButNotSuperAdminOnlyOrUngrantedOnes() {
        User admin = User.builder().id(2L).userType(UserTypeEnum.ADMIN).roleId(5L).build();

        NavLink ungated = link("dashboard", null, false);
        NavLink grantedPermission = link("manage-users", "USER_MANAGE", false);
        NavLink deniedPermission = link("manage-roles", "ROLE_MANAGE", false);
        NavLink superAdminOnly = link("platform-settings", null, true);

        when(navLinkRepository.findByStatusOrderByNavGroupAscNavIndexAsc(NavLinkStatusEnum.ACTIVE))
                .thenReturn(List.of(ungated, grantedPermission, deniedPermission, superAdminOnly));
        when(adminPermissionGuard.has("USER_MANAGE")).thenReturn(true);
        when(adminPermissionGuard.has("ROLE_MANAGE")).thenReturn(false);

        List<NavLinkResponse> result = navLinkService.getMine(admin);

        assertThat(result).extracting(NavLinkResponse::getName)
                .containsExactlyInAnyOrder("dashboard", "manage-users");
    }

    @Test
    void superAdminSeesEverythingUnfilteredAndNeverConsultsTheGuard() {
        User superAdmin = User.builder().id(3L).userType(UserTypeEnum.SUPER_ADMIN).build();

        NavLink ungated = link("dashboard", null, false);
        NavLink gatedByPermission = link("manage-roles", "ROLE_MANAGE", false);
        NavLink superAdminOnly = link("platform-settings", null, true);

        when(navLinkRepository.findByStatusOrderByNavGroupAscNavIndexAsc(NavLinkStatusEnum.ACTIVE))
                .thenReturn(List.of(ungated, gatedByPermission, superAdminOnly));

        List<NavLinkResponse> result = navLinkService.getMine(superAdmin);

        assertThat(result).extracting(NavLinkResponse::getName)
                .containsExactlyInAnyOrder("dashboard", "manage-roles", "platform-settings");
        verifyNoInteractions(adminPermissionGuard);
    }
}
