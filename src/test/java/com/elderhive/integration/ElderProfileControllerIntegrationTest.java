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

    // ==== FamilyElderLink (Prompt 62) ====

    private Long createProfile(Cookie ownerCookie, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/elder-profiles").cookie(ownerCookie)
                        .contentType("application/json").content(profileJson(fullName)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    void theOwnerCanAddACoManagingFamilyMemberAndThatMemberCanThenAccessTheProfile() throws Exception {
        User owner = persistUser("fel-add-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-add-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Shared Elder");

        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(owner))
                        .contentType("application/json")
                        .content("{\"familyUserId\":%d,\"relationshipLabel\":\"Son\"}".formatted(coManager.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.relationshipLabel").value("Son"))
                .andExpect(jsonPath("$.data.isOwner").value(false));

        // Was a stranger before being linked - now has full standing on the profile.
        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(loginCookieFor(coManager)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/elder-profiles").cookie(loginCookieFor(coManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Shared Elder"));
    }

    @Test
    void getFamilyMembersListsTheOwnerAndEveryCoManager() throws Exception {
        User owner = persistUser("fel-list-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-list-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Elder");
        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(owner))
                .contentType("application/json")
                .content("{\"familyUserId\":%d,\"relationshipLabel\":\"Niece\"}".formatted(coManager.getId())));

        mockMvc.perform(get("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isOwner").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(owner.getId()))
                .andExpect(jsonPath("$.data[1].userId").value(coManager.getId()))
                .andExpect(jsonPath("$.data[1].relationshipLabel").value("Niece"));
    }

    @Test
    void aStrangerCannotAddOrViewOrRemoveFamilyMembers() throws Exception {
        User owner = persistUser("fel-stranger-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User stranger = persistUser("fel-stranger-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        User target = persistUser("fel-stranger-target@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Elder");

        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(stranger))
                        .contentType("application/json")
                        .content("{\"familyUserId\":%d}".formatted(target.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addingANonFamilyMemberAccountReturns400BadRequestViaInvalidRole() throws Exception {
        User owner = persistUser("fel-invalidrole-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretaker = persistUser("fel-invalidrole-caretaker@example.com", UserTypeEnum.CARETAKER);
        Long id = createProfile(loginCookieFor(owner), "Elder");

        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(loginCookieFor(owner))
                        .contentType("application/json")
                        .content("{\"familyUserId\":%d}".formatted(caretaker.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingTheSameCoManagerTwiceOrTheOwnerThemselvesConflicts() throws Exception {
        User owner = persistUser("fel-conflict-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-conflict-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Elder");
        Cookie ownerCookie = loginCookieFor(owner);

        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(ownerCookie)
                .contentType("application/json").content("{\"familyUserId\":%d}".formatted(coManager.getId())));

        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(ownerCookie)
                        .contentType("application/json").content("{\"familyUserId\":%d}".formatted(coManager.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(ownerCookie)
                        .contentType("application/json").content("{\"familyUserId\":%d}".formatted(owner.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void theOwnerCanRemoveACoManagerWhoThenLosesAccess() throws Exception {
        User owner = persistUser("fel-remove-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManager = persistUser("fel-remove-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Elder");
        Cookie ownerCookie = loginCookieFor(owner);
        mockMvc.perform(post("/api/v1/elder-profiles/" + id + "/family-members").cookie(ownerCookie)
                .contentType("application/json").content("{\"familyUserId\":%d}".formatted(coManager.getId())));

        mockMvc.perform(delete("/api/v1/elder-profiles/" + id + "/family-members/" + coManager.getId()).cookie(ownerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/elder-profiles/" + id).cookie(loginCookieFor(coManager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void removingAUserWhoIsNotLinkedReturns404() throws Exception {
        User owner = persistUser("fel-remove-404-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User notLinked = persistUser("fel-remove-404-notlinked@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long id = createProfile(loginCookieFor(owner), "Elder");

        mockMvc.perform(delete("/api/v1/elder-profiles/" + id + "/family-members/" + notLinked.getId())
                        .cookie(loginCookieFor(owner)))
                .andExpect(status().isNotFound());
    }
}
