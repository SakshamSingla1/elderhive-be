package com.elderhive.integration;

import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserRoleMappingRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 93) - list/
 * search/suspend users plus multi-role grant/revoke, including the extra SUPER_ADMIN-only
 * restriction on granting/revoking ADMIN or SUPER_ADMIN specifically.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleMappingRepository userRoleMappingRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").fullName("Test User").userType(userType).status(UserStatusEnum.ACTIVE).build());
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(userType).isPrimary(true).build());
        return user;
    }

    // AdminBootstrapSeeder (Prompt 24) seeds a curated "Admin" role with USER_MANAGE (among
    // every other non-Super-Admin-only permission) on every boot - same precedent
    // AdminDashboardControllerIntegrationTest's own admin fixture uses.
    private User persistAdminWithUserManage(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        User user = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.ADMIN).isPrimary(true).build());
        return user;
    }

    private User persistSuperAdmin(String email) {
        User user = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.SUPER_ADMIN).status(UserStatusEnum.ACTIVE).build());
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId()).userType(UserTypeEnum.SUPER_ADMIN).isPrimary(true).build());
        return user;
    }

    @Test
    void aPlainFamilyMemberIsForbiddenFromListingUsers() throws Exception {
        User family = persistUser("adminusers-forbidden-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/admin/users").cookie(loginCookieFor(family)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminWithUserManageCanSearchUsersByEmail() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-search-admin@example.com");
        persistUser("adminusers-search-target-zqx@example.com", UserTypeEnum.FAMILY_MEMBER);
        persistUser("adminusers-search-other@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(get("/api/v1/admin/users").cookie(loginCookieFor(admin)).param("search", "search-target-zqx"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
                    assertThat(data.get("content")).hasSize(1);
                    assertThat(data.get("content").get(0).get("email").asText()).isEqualTo("adminusers-search-target-zqx@example.com");
                });
    }

    @Test
    void anAdminWithUserManageCanSuspendAUser() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-suspend-admin@example.com");
        User target = persistUser("adminusers-suspend-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(put("/api/v1/admin/users/" + target.getId() + "/suspend").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
                    assertThat(data.get("status").asText()).isEqualTo("SUSPENDED");
                });

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatusEnum.SUSPENDED);
    }

    @Test
    void anAdminWithUserManageCanGrantAndThenRevokeAnOrdinaryRole() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-grant-admin@example.com");
        User target = persistUser("adminusers-grant-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        MvcResult grantResult = mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/roles").cookie(loginCookieFor(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleType", "CARETAKER"))))
                .andExpect(status().isOk())
                .andReturn();
        var grantedData = objectMapper.readTree(grantResult.getResponse().getContentAsString()).get("data");
        assertThat(grantedData.get("roles")).extracting(n -> n.asText()).contains("FAMILY_MEMBER", "CARETAKER");
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(target.getId(), UserTypeEnum.CARETAKER)).isTrue();

        MvcResult revokeResult = mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/roles/CARETAKER").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andReturn();
        var revokedData = objectMapper.readTree(revokeResult.getResponse().getContentAsString()).get("data");
        assertThat(revokedData.get("roles")).extracting(n -> n.asText()).containsExactly("FAMILY_MEMBER");
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(target.getId(), UserTypeEnum.CARETAKER)).isFalse();
    }

    @Test
    void grantingTheSameRoleTwiceIsRejectedAsConflict() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-dupgrant-admin@example.com");
        User target = persistUser("adminusers-dupgrant-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/roles").cookie(loginCookieFor(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleType", "FAMILY_MEMBER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void revokingAUsersPrimaryRoleIsRejectedAsConflict() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-revokeprimary-admin@example.com");
        User target = persistUser("adminusers-revokeprimary-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/roles/FAMILY_MEMBER").cookie(loginCookieFor(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void aPlainAdminCannotGrantTheAdminRole() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-plainadmin-grant@example.com");
        User target = persistUser("adminusers-plainadmin-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/roles").cookie(loginCookieFor(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleType", "ADMIN"))))
                .andExpect(status().isForbidden());
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(target.getId(), UserTypeEnum.ADMIN)).isFalse();
    }

    @Test
    void aSuperAdminCanGrantAndRevokeTheAdminRole() throws Exception {
        User superAdmin = persistSuperAdmin("adminusers-superadmin-grant@example.com");
        User target = persistUser("adminusers-superadmin-target@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/roles").cookie(loginCookieFor(superAdmin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleType", "ADMIN"))))
                .andExpect(status().isOk());
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(target.getId(), UserTypeEnum.ADMIN)).isTrue();

        mockMvc.perform(delete("/api/v1/admin/users/" + target.getId() + "/roles/ADMIN").cookie(loginCookieFor(superAdmin)))
                .andExpect(status().isOk());
        assertThat(userRoleMappingRepository.existsByUserIdAndUserType(target.getId(), UserTypeEnum.ADMIN)).isFalse();
    }

    @Test
    void aSuperAdminCanAssignAndThenClearAFineGrainedRoleOnAPlainAdmin() throws Exception {
        User superAdmin = persistSuperAdmin("adminusers-assignrole-superadmin@example.com");
        User target = persistUser("adminusers-assignrole-target@example.com", UserTypeEnum.ADMIN);
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();

        MvcResult assignResult = mockMvc.perform(put("/api/v1/admin/users/" + target.getId() + "/role").cookie(loginCookieFor(superAdmin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleId", adminRole.getId()))))
                .andExpect(status().isOk())
                .andReturn();
        var assignedData = objectMapper.readTree(assignResult.getResponse().getContentAsString()).get("data");
        assertThat(assignedData.get("roleId").asLong()).isEqualTo(adminRole.getId());
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRoleId()).isEqualTo(adminRole.getId());

        MvcResult clearResult = mockMvc.perform(put("/api/v1/admin/users/" + target.getId() + "/role").cookie(loginCookieFor(superAdmin))
                        .contentType("application/json")
                        .content("{\"roleId\":null}"))
                .andExpect(status().isOk())
                .andReturn();
        var clearedData = objectMapper.readTree(clearResult.getResponse().getContentAsString()).get("data");
        assertThat(clearedData.get("roleId").isNull()).isTrue();
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRoleId()).isNull();
    }

    @Test
    void aPlainAdminCannotAssignFineGrainedRolesEvenWithUserManage() throws Exception {
        User admin = persistAdminWithUserManage("adminusers-assignrole-plainadmin@example.com");
        User target = persistUser("adminusers-assignrole-forbidden-target@example.com", UserTypeEnum.ADMIN);
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();

        mockMvc.perform(put("/api/v1/admin/users/" + target.getId() + "/role").cookie(loginCookieFor(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleId", adminRole.getId()))))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRoleId()).isNull();
    }

    @Test
    void assigningANonexistentRoleIdReturnsNotFound() throws Exception {
        User superAdmin = persistSuperAdmin("adminusers-assignrole-badid-superadmin@example.com");
        User target = persistUser("adminusers-assignrole-badid-target@example.com", UserTypeEnum.ADMIN);

        mockMvc.perform(put("/api/v1/admin/users/" + target.getId() + "/role").cookie(loginCookieFor(superAdmin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("roleId", 999_999_999L))))
                .andExpect(status().isNotFound());
    }
}
