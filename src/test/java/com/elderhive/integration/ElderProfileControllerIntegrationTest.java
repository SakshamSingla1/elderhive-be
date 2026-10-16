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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ElderProfileControllerIntegrationTest {

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
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private String profileJson(String fullName) {
        return """
                { "fullName": "%s", "dateOfBirth": "1945-03-12", "gender": "MALE",
                  "medicalConditions": "Hypertension", "address": "123 Main St",
                  "emergencyContactName": "Jane", "emergencyContactPhone": "+1-555-0100" }
                """.formatted(fullName);
    }

    @Test
    void aFamilyMemberCreatingAProfileOwnsItRegardlessOfAnySuppliedIds() throws Exception {
        User family = persistUser("ep-create-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User someoneElse = persistUser("ep-create-someone-else@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie cookie = loginCookieFor(family);

        // Tries to sneak in someoneElse's id as the owner - must be ignored.
        String body = """
                { "familyUserId": %d, "fullName": "Robert Smith" }
                """.formatted(someoneElse.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/elder-profiles").cookie(cookie)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("familyUserId").asLong()).isEqualTo(family.getId());
        assertThat(data.get("elderUserId").isNull()).isTrue();
    }

    @Test
    void anElderCreatingAProfileSelfManagesItRegardlessOfRequestBody() throws Exception {
        User elder = persistUser("ep-create-elder@example.com", UserTypeEnum.ELDER);
        Cookie cookie = loginCookieFor(elder);

        mockMvc.perform(post("/api/v1/elder-profiles").cookie(cookie)
                        .contentType("application/json").content(profileJson("Self Managed")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.elderUserId").value(elder.getId()))
                .andExpect(jsonPath("$.data.familyUserId").value(nullValue()));

        mockMvc.perform(get("/api/v1/elder-profiles/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Self Managed"));
    }

    @Test
    void anAdminCreatingAProfileCanSetBothOwnerIdsExplicitly() throws Exception {
        User admin = persistUser("ep-create-admin@example.com", UserTypeEnum.SUPER_ADMIN);
        User family = persistUser("ep-create-admin-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie cookie = loginCookieFor(admin);

        String body = """
                { "familyUserId": %d, "fullName": "Admin Created" }
                """.formatted(family.getId());

        mockMvc.perform(post("/api/v1/elder-profiles").cookie(cookie)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.familyUserId").value(family.getId()));
    }

    @Test
    void aFamilyMemberSeesOnlyTheirOwnProfilesButAnAdminSeesEveryProfile() throws Exception {
        User family = persistUser("ep-list-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User otherFamily = persistUser("ep-list-other-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistUser("ep-list-admin@example.com", UserTypeEnum.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/elder-profiles").cookie(loginCookieFor(family))
                .contentType("application/json").content(profileJson("Mine")));
        mockMvc.perform(post("/api/v1/elder-profiles").cookie(loginCookieFor(otherFamily))
                .contentType("application/json").content(profileJson("Not Mine")));

        mockMvc.perform(get("/api/v1/elder-profiles").cookie(loginCookieFor(family)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Mine"));

        mockMvc.perform(get("/api/v1/elder-profiles").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void aStrangerGets403ButTheOwnerAndAnAdminCanReachTheSameProfile() throws Exception {
        User family = persistUser("ep-access-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User stranger = persistUser("ep-access-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistUser("ep-access-admin@example.com", UserTypeEnum.SUPER_ADMIN);

        MvcResult createResult = mockMvc.perform(post("/api/v1/elder-profiles").cookie(loginCookieFor(family))
                        .contentType("application/json").content(profileJson("Protected")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(loginCookieFor(family)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(loginCookieFor(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void updatingAnUnknownIdReturns404() throws Exception {
        User family = persistUser("ep-update-unknown@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(put("/api/v1/elder-profiles/999999999").cookie(loginCookieFor(family))
                        .contentType("application/json").content(profileJson("X")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theOwnerCanUpdateAndThenDeleteTheirOwnProfile() throws Exception {
        User family = persistUser("ep-update-delete@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie cookie = loginCookieFor(family);

        MvcResult createResult = mockMvc.perform(post("/api/v1/elder-profiles").cookie(cookie)
                        .contentType("application/json").content(profileJson("Original")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/elder-profiles/" + id).cookie(cookie)
                        .contentType("application/json").content(profileJson("Renamed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed"));

        mockMvc.perform(delete("/api/v1/elder-profiles/" + id).cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/elder-profiles")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/elder-profiles").contentType("application/json").content(profileJson("X")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAProfileWithAMissingFullNameFailsValidation() throws Exception {
        User family = persistUser("ep-validation@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/elder-profiles").cookie(loginCookieFor(family))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
