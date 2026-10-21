package com.elderhive.integration;

import com.elderhive.entities.Role;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.User;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.ServiceCategoryRepository;
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
 * granted to the seeded "Admin" role (AdminBootstrapSeeder), same gating precedent as
 * ServiceCategoryControllerIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceOfferingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

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

    private User persistFamilyMember(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.FAMILY_MEMBER).status(UserStatusEnum.ACTIVE).build());
    }

    private Long persistCategory(String name) {
        return serviceCategoryRepository.save(ServiceCategory.builder().name(name).status(ServiceCategoryStatusEnum.ACTIVE).build()).getId();
    }

    private String offeringJson(String name, Long categoryId) {
        return "{\"name\":\"%s\",\"categoryId\":%d,\"description\":\"desc\",\"basePrice\":50.00,\"durationMinutes\":60}"
                .formatted(name, categoryId);
    }

    @Test
    void anAdminWithServiceManageCanCreateListGetUpdateAndDeleteAnOffering() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svcoffer-crud-admin@example.com"));
        Long categoryId = persistCategory("svcoffer-crud-Temple Visit");

        MvcResult createResult = mockMvc.perform(post("/api/v1/service-offerings").cookie(cookie)
                        .contentType("application/json").content(offeringJson("Weekly Temple Visit", categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Weekly Temple Visit"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/service-offerings/" + id).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Weekly Temple Visit"));

        mockMvc.perform(get("/api/v1/service-offerings").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(put("/api/v1/service-offerings/" + id).cookie(cookie)
                        .contentType("application/json")
                        .content("{\"name\":\"Monthly Temple Visit\",\"categoryId\":" + categoryId + ",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Monthly Temple Visit"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/service-offerings/" + id).cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/service-offerings/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAnOfferingWithAnUnknownCategoryReturns404() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svcoffer-badcategory-admin@example.com"));

        mockMvc.perform(post("/api/v1/service-offerings").cookie(cookie)
                        .contentType("application/json").content(offeringJson("X", 999999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAnOfferingWithMissingRequiredFieldsFailsValidation() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svcoffer-validation-admin@example.com"));

        mockMvc.perform(post("/api/v1/service-offerings").cookie(cookie)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonAdminCallerIsForbidden() throws Exception {
        Cookie cookie = loginCookieFor(persistFamilyMember("svcoffer-family@example.com"));

        mockMvc.perform(get("/api/v1/service-offerings").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/service-offerings")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatingOrDeletingAnUnknownOfferingReturns404() throws Exception {
        Cookie cookie = loginCookieFor(persistServiceManagingAdmin("svcoffer-404-admin@example.com"));
        Long categoryId = persistCategory("svcoffer-404-Temple Visit");

        mockMvc.perform(put("/api/v1/service-offerings/999999999").cookie(cookie)
                        .contentType("application/json").content(offeringJson("X", categoryId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/service-offerings/999999999").cookie(cookie))
                .andExpect(status().isNotFound());
    }
}
