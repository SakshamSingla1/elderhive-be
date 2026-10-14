package com.elderhive.integration;

import com.elderhive.entities.NavLink;
import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.NavLinkStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.NavLinkRepository;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of Phase 3 (Prompts 20-26) as a whole, over the real dispatched
 * Spring Security + MVC stack (not individual unit tests of one class) — the same thing
 * manually exercised with curl/psql while building each prompt, now pinned down as an
 * automated regression suite so a later phase can't silently break this RBAC wiring.
 *
 * <p>Relies on AdminBootstrapSeeder (Prompt 24) having already run — same assumption
 * AdminBootstrapSeederTest documents: the seeder runs once per ApplicationContext
 * refresh, so by the time any test method here executes, the real "Super Admin"/"Admin"
 * roles and the 10-code permission catalog already exist in Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Phase3RbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private NavLinkRepository navLinkRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType, Long roleId) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .userType(userType)
                .status(UserStatusEnum.ACTIVE)
                .roleId(roleId)
                .build());
    }

    private List<String> namesFromMeResponse(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        List<String> names = new ArrayList<>();
        StreamSupport.stream(data.spliterator(), false).forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    // ==== The bootstrap-seeded "Admin" role genuinely lacks ROLE_MANAGE/CONFIG_MANAGE ====

    @Test
    void seededAdminRoleLacksRoleManageAndConfigManageButHasEverythingElse() {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        Long roleManageId = permissionRepository.findByCode("ROLE_MANAGE").orElseThrow().getId();
        Long configManageId = permissionRepository.findByCode("CONFIG_MANAGE").orElseThrow().getId();
        Long userManageId = permissionRepository.findByCode("USER_MANAGE").orElseThrow().getId();
        Long navLinkManageId = permissionRepository.findByCode("NAVLINK_MANAGE").orElseThrow().getId();

        assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), roleManageId)).isFalse();
        assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), configManageId)).isFalse();

        // Not simply "the role has nothing" - it has every OTHER catalog permission.
        assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), userManageId)).isTrue();
        assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), navLinkManageId)).isTrue();
    }

    // ==== GET /nav-links/me differs by caller ====

    @Test
    void navLinksMeReturnsDifferentListsForSuperAdminCuratedAdminAndEveryoneElse() throws Exception {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();

        navLinkRepository.save(NavLink.builder().name("Phase3 Ungated").path("/phase3/ungated")
                .navIndex(1).status(NavLinkStatusEnum.ACTIVE).build());
        navLinkRepository.save(NavLink.builder().name("Phase3 NavLinkManage").path("/phase3/navlink-manage")
                .navIndex(2).requiredPermission("NAVLINK_MANAGE").status(NavLinkStatusEnum.ACTIVE).build());
        navLinkRepository.save(NavLink.builder().name("Phase3 RoleManage").path("/phase3/role-manage")
                .navIndex(3).requiredPermission("ROLE_MANAGE").status(NavLinkStatusEnum.ACTIVE).build());
        navLinkRepository.save(NavLink.builder().name("Phase3 SuperAdminOnly").path("/phase3/super-only")
                .navIndex(4).superAdminOnly(true).status(NavLinkStatusEnum.ACTIVE).build());

        User superAdmin = persistUser("phase3-superadmin@example.com", UserTypeEnum.SUPER_ADMIN, null);
        User admin = persistUser("phase3-admin@example.com", UserTypeEnum.ADMIN, adminRole.getId());
        User elder = persistUser("phase3-elder@example.com", UserTypeEnum.ELDER, null);
        User familyMember = persistUser("phase3-family@example.com", UserTypeEnum.FAMILY_MEMBER, null);
        User caretaker = persistUser("phase3-caretaker@example.com", UserTypeEnum.CARETAKER, null);

        MvcResult superAdminResult = mockMvc.perform(get("/nav-links/me").cookie(loginCookieFor(superAdmin)))
                .andExpect(status().isOk()).andReturn();
        MvcResult adminResult = mockMvc.perform(get("/nav-links/me").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk()).andReturn();

        List<String> superAdminNames = namesFromMeResponse(superAdminResult);
        List<String> adminNames = namesFromMeResponse(adminResult);

        // SUPER_ADMIN: every ACTIVE row, unfiltered - including requiredPermission rows it
        // was never assigned and the superAdminOnly row.
        assertThat(superAdminNames).containsExactlyInAnyOrder(
                "Phase3 Ungated", "Phase3 NavLinkManage", "Phase3 RoleManage", "Phase3 SuperAdminOnly");

        // The curated "Admin" role: ungated + the permission it genuinely has
        // (NAVLINK_MANAGE), but not the one it lacks (ROLE_MANAGE) and never the
        // superAdminOnly row.
        assertThat(adminNames).containsExactlyInAnyOrder("Phase3 Ungated", "Phase3 NavLinkManage");

        // ELDER/FAMILY_MEMBER/CARETAKER: empty - their nav is FE-hardcoded, not DB-driven.
        for (User nonAdminCaller : List.of(elder, familyMember, caretaker)) {
            assertThat(namesFromMeResponse(
                    mockMvc.perform(get("/nav-links/me").cookie(loginCookieFor(nonAdminCaller)))
                            .andExpect(status().isOk()).andReturn()))
                    .isEmpty();
        }
    }

    // ==== An ADMIN without ROLE_MANAGE is denied on role-management endpoints ====

    @Test
    void curatedAdminIsDeniedOnEveryRoleAndPermissionManagementEndpoint() throws Exception {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        User admin = persistUser("phase3-denied-admin@example.com", UserTypeEnum.ADMIN, adminRole.getId());
        Cookie cookie = loginCookieFor(admin);

        mockMvc.perform(get("/roles").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(post("/roles").cookie(cookie).contentType("application/json")
                        .content("{\"name\":\"Should Not Be Created\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/permissions").cookie(cookie)).andExpect(status().isForbidden());

        Long someRoleId = adminRole.getId();
        Long somePermissionId = permissionRepository.findByCode("ROLE_MANAGE").orElseThrow().getId();
        mockMvc.perform(post("/permissions/roles/" + someRoleId + "/" + somePermissionId).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/permissions/roles/" + someRoleId + "/" + somePermissionId).cookie(cookie))
                .andExpect(status().isForbidden());
    }

    // ==== A SUPER_ADMIN can do everything an ADMIN can, plus role/permission management ====

    @Test
    void superAdminCanDoEverythingACuratedAdminCanPlusRoleAndPermissionManagement() throws Exception {
        User superAdmin = persistUser("phase3-capable-superadmin@example.com", UserTypeEnum.SUPER_ADMIN, null);
        Cookie cookie = loginCookieFor(superAdmin);

        // Everything a curated Admin can already do (NAVLINK_MANAGE-gated):
        mockMvc.perform(post("/nav-links").cookie(cookie).contentType("application/json")
                        .content("{\"name\":\"Phase3 SuperAdmin NavLink\",\"path\":\"/phase3/superadmin-created\"}"))
                .andExpect(status().isCreated());

        // Plus what the curated Admin role is explicitly denied: role/permission management.
        MvcResult createRoleResult = mockMvc.perform(post("/roles").cookie(cookie).contentType("application/json")
                        .content("{\"name\":\"Phase3 SuperAdmin Role\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn();
        long newRoleId = objectMapper.readTree(createRoleResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/permissions").cookie(cookie)).andExpect(status().isOk());

        Long dashboardViewId = permissionRepository.findByCode("DASHBOARD_VIEW").orElseThrow().getId();
        mockMvc.perform(post("/permissions/roles/" + newRoleId + "/" + dashboardViewId).cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/roles/" + newRoleId).cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/roles/" + newRoleId).cookie(cookie))
                .andExpect(status().isOk());
    }
}
