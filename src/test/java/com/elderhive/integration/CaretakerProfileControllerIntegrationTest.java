package com.elderhive.integration;

import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.User;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaretakerProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).fullName("Test " + email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private Long persistCategory(String name) {
        return serviceCategoryRepository.save(ServiceCategory.builder().name(name).status(ServiceCategoryStatusEnum.ACTIVE).build()).getId();
    }

    private String profileJson(String bio, Long categoryId) {
        return """
                { "bio": "%s", "specialties": [%d], "yearsOfExperience": 5,
                  "hourlyRate": 25.50, "latitude": 12.9716, "longitude": 77.5946, "serviceRadiusKm": 10 }
                """.formatted(bio, categoryId);
    }

    @Test
    void aCaretakerCanCreateThenUpdateTheirOwnProfile() throws Exception {
        User caretaker = persistUser("cp-create@example.com", UserTypeEnum.CARETAKER);
        Long categoryId = persistCategory("cp-create-Mobility Assistance");
        Cookie cookie = loginCookieFor(caretaker);

        MvcResult createResult = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content(profileJson("Original bio", categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(caretaker.getId()))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.ratingAverage").value(0.0))
                .andExpect(jsonPath("$.data.fullName").value(caretaker.getFullName()))
                .andExpect(jsonPath("$.data.specialties[0].id").value(categoryId))
                .andExpect(jsonPath("$.data.specialties[0].name").value("cp-create-Mobility Assistance"))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content(profileJson("Updated bio", categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.bio").value("Updated bio"));

        mockMvc.perform(get("/api/v1/caretaker-profiles/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Updated bio"));
    }

    @Test
    void aNonCaretakerCannotCreateAProfile() throws Exception {
        User family = persistUser("cp-nonc@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long categoryId = persistCategory("cp-nonc-Mobility Assistance");

        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(family))
                        .contentType("application/json").content(profileJson("Bio", categoryId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyAuthenticatedUserCanViewAProfileByIdOrBrowseTheList() throws Exception {
        User caretaker = persistUser("cp-view@example.com", UserTypeEnum.CARETAKER);
        User family = persistUser("cp-viewer-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long categoryId = persistCategory("cp-view-Mobility Assistance");
        MvcResult createResult = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(caretaker))
                        .contentType("application/json").content(profileJson("Viewable bio", categoryId)))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + id).cookie(loginCookieFor(family)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Viewable bio"));

        mockMvc.perform(get("/api/v1/caretaker-profiles").cookie(loginCookieFor(family)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void gettingAnUnknownProfileByIdOrMeWithNoneCreatedReturns404() throws Exception {
        User caretaker = persistUser("cp-404@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(get("/api/v1/caretaker-profiles/999999999").cookie(loginCookieFor(caretaker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/caretaker-profiles/me").cookie(loginCookieFor(caretaker)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/caretaker-profiles")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/caretaker-profiles").contentType("application/json").content(profileJson("X", 1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAProfileWithANegativeHourlyRateFailsValidation() throws Exception {
        User caretaker = persistUser("cp-validation@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(caretaker))
                        .contentType("application/json")
                        .content("{\"hourlyRate\": -5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAProfileWithoutAnySpecialtiesStillSucceeds() throws Exception {
        User caretaker = persistUser("cp-nospecialty@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(caretaker))
                        .contentType("application/json").content("{\"bio\": \"No specialties yet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("No specialties yet"));
    }

    @Test
    void creatingAProfileWithAnUnknownCategoryIdReturns404() throws Exception {
        User caretaker = persistUser("cp-badcategory@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(caretaker))
                        .contentType("application/json").content(profileJson("Bio", 999999999L)))
                .andExpect(status().isNotFound());
    }
}
