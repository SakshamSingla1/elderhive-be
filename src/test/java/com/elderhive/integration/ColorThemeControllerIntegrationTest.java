package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack, mirroring
 * Phase3RbacIntegrationTest's approach. Proves the controller's direct
 * hasRole('SUPER_ADMIN') gate on mutations (NOT the Phase 3 AdminPermissionGuard/
 * ROLE_MANAGE-style fine-grained system — a deliberate difference for this
 * platform-level asset, same as Portfolio) and that every authenticated role,
 * including non-admin ones, can read the catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ColorThemeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .userType(userType)
                .status(UserStatusEnum.ACTIVE)
                .build());
    }

    private String themeRequestJson(String themeName, String status) {
        return """
                {
                  "themeName": "%s",
                  "status": "%s",
                  "palette": {
                    "colorGroups": [
                      {
                        "groupName": "primary",
                        "colorShades": [
                          {"colorName": "primary500", "colorCode": "#2E7D32"}
                        ]
                      }
                    ]
                  }
                }
                """.formatted(themeName, status);
    }

    // ==== SUPER_ADMIN can do everything (create/update/get/list/delete) ====

    @Test
    void superAdminCanCreateUpdateGetListAndDeleteAColorTheme() throws Exception {
        User superAdmin = persistUser("ct-superadmin@example.com", UserTypeEnum.SUPER_ADMIN);
        Cookie cookie = loginCookieFor(superAdmin);

        MvcResult createResult = mockMvc.perform(post("/api/v1/color-themes").cookie(cookie)
                        .contentType("application/json")
                        .content(themeRequestJson("Forest Green", "ACTIVE")))
                .andExpect(status().isCreated()).andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("themeName").asText()).isEqualTo("Forest Green");
        assertThat(created.get("palette").get("colorGroups").get(0).get("groupName").asText()).isEqualTo("primary");

        mockMvc.perform(put("/api/v1/color-themes/" + id).cookie(cookie)
                        .contentType("application/json")
                        .content(themeRequestJson("Forest Green Renamed", "ACTIVE")))
                .andExpect(status().isOk());

        MvcResult getResult = mockMvc.perform(get("/api/v1/color-themes/" + id).cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(getResult.getResponse().getContentAsString())
                .get("data").get("themeName").asText()).isEqualTo("Forest Green Renamed");

        mockMvc.perform(get("/api/v1/color-themes").cookie(cookie)
                        .param("search", "Renamed").param("status", "ACTIVE"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/color-themes/" + id).cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/color-themes/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    // ==== An ADMIN (even one with full Phase 3 permissions) is denied on mutations ====

    @Test
    void adminIsDeniedOnCreateUpdateAndDeleteButCanStillRead() throws Exception {
        User admin = persistUser("ct-admin@example.com", UserTypeEnum.ADMIN);
        Cookie cookie = loginCookieFor(admin);

        mockMvc.perform(post("/api/v1/color-themes").cookie(cookie)
                        .contentType("application/json")
                        .content(themeRequestJson("Admin Attempt", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/color-themes/1").cookie(cookie)
                        .contentType("application/json")
                        .content(themeRequestJson("Admin Attempt", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/color-themes/1").cookie(cookie))
                .andExpect(status().isForbidden());

        // Reads are NOT gated - an ADMIN can still list.
        mockMvc.perform(get("/api/v1/color-themes").cookie(cookie))
                .andExpect(status().isOk());
    }

    // ==== Every authenticated role (including non-admin ones) can browse the catalog ====

    @Test
    void everyAuthenticatedRoleCanBrowseTheCatalogToApplyAThemeToItsOwnDashboard() throws Exception {
        User superAdmin = persistUser("ct-browse-superadmin@example.com", UserTypeEnum.SUPER_ADMIN);
        MvcResult createResult = mockMvc.perform(post("/api/v1/color-themes").cookie(loginCookieFor(superAdmin))
                        .contentType("application/json")
                        .content(themeRequestJson("Browsable Theme", "ACTIVE")))
                .andExpect(status().isCreated()).andReturn();
        long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        for (UserTypeEnum userType : new UserTypeEnum[]{UserTypeEnum.ELDER, UserTypeEnum.FAMILY_MEMBER, UserTypeEnum.CARETAKER}) {
            User user = persistUser("ct-browse-" + userType.name().toLowerCase() + "@example.com", userType);
            Cookie cookie = loginCookieFor(user);

            mockMvc.perform(get("/api/v1/color-themes/" + id).cookie(cookie)).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/color-themes").cookie(cookie)).andExpect(status().isOk());
        }
    }

    // ==== Unauthenticated callers are rejected entirely ====

    @Test
    void unauthenticatedCallerIsRejectedOnBothReadsAndMutations() throws Exception {
        mockMvc.perform(get("/api/v1/color-themes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/color-themes").contentType("application/json")
                        .content(themeRequestJson("Anon", "ACTIVE")))
                .andExpect(status().isUnauthorized());
    }
}
