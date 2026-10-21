package com.elderhive.integration;

import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack. SERVICE_MANAGE is
 * granted to the seeded "Admin" role (AdminBootstrapSeeder - it's not in
 * SUPER_ADMIN_ONLY_CODES), so an ADMIN with that role assigned passes; a plain ADMIN with
 * no roleId, or a non-admin caller, must not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceCategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistServiceManagingAdmin(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
    }

    private User persistPlainAdmin(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).status(UserStatusEnum.ACTIVE).build());
    }

    private User persistFamilyMember(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.FAMILY_MEMBER).status(UserStatusEnum.ACTIVE).build());
    }

    private String categoryJson(String name) {
        return "{\"name\":\"%s\",\"description\":\"desc\",\"icon\":\"icon\"}".formatted(name);
    }

    @Test
    void anAdminWithServiceManageCanCreateListGetUpdateAndDeleteACategory() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svccat-crud-admin@example.com"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/service-categories").cookie(cookie)
                        .contentType("application/json").content(categoryJson("Overnight Care")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Overnight Care"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/service-categories/" + id).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Overnight Care"));

        mockMvc.perform(get("/api/v1/service-categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(put("/api/v1/service-categories/" + id).cookie(cookie)
                        .contentType("application/json").content("{\"name\":\"Overnight Care\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/service-categories/" + id).cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/service-categories/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingACategoryWithADuplicateNameReturns409() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svccat-dup-admin@example.com"));
        mockMvc.perform(post("/api/v1/service-categories").cookie(cookie)
                .contentType("application/json").content(categoryJson("Pet Walking")));

        mockMvc.perform(post("/api/v1/service-categories").cookie(cookie)
                        .contentType("application/json").content(categoryJson("Pet Walking")))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingACategoryWithAMissingNameFailsValidation() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svccat-validation-admin@example.com"));

        mockMvc.perform(post("/api/v1/service-categories").cookie(cookie)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPlainAdminWithNoRoleAssignedIsForbidden() throws Exception {
        Cookie cookie = loginCookieFor(persistPlainAdmin("svccat-norole-admin@example.com"));

        mockMvc.perform(get("/api/v1/service-categories").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/service-categories").cookie(cookie)
                        .contentType("application/json").content(categoryJson("X")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonAdminCallerIsForbidden() throws Exception {
        Cookie cookie = loginCookieFor(persistFamilyMember("svccat-family@example.com"));

        mockMvc.perform(get("/api/v1/service-categories").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/service-categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatingOrDeletingAnUnknownCategoryReturns404() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svccat-404-admin@example.com"));

        mockMvc.perform(put("/api/v1/service-categories/999999999").cookie(cookie)
                        .contentType("application/json").content(categoryJson("X")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/service-categories/999999999").cookie(cookie))
                .andExpect(status().isNotFound());
    }
}
