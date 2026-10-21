package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
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
class SearchControllerIntegrationTest {

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

    private User persistUser(String email, String fullName, UserTypeEnum type) {
        return userRepository.save(User.builder()
                .email(email).fullName(fullName).passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void searchingCaretakersByNameReturnsAMatchingLightweightResult() throws Exception {
        User caretakerUser = persistUser("search-caretaker@example.com", "Carla Caretaker", UserTypeEnum.CARETAKER);
        User searcher = persistUser("search-searcher@example.com", "Someone", UserTypeEnum.FAMILY_MEMBER);
        Cookie caretakerCookie = loginCookieFor(caretakerUser);
        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(caretakerCookie)
                .contentType("application/json")
                .content("{\"bio\":\"Bio\",\"specialties\":[\"Mobility Assistance\"],\"latitude\":12.9716,\"longitude\":77.5946}"));

        mockMvc.perform(get("/api/v1/search/caretakers").param("query", "carla").cookie(loginCookieFor(searcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Carla Caretaker"))
                .andExpect(jsonPath("$.data[0].specialtiesSummary").value("Mobility Assistance"))
                .andExpect(jsonPath("$.data[0].verificationStatus").value("PENDING"));
    }

    @Test
    void searchingCaretakersByCoordinatesReturnsSortedByDistanceWithNoQuery() throws Exception {
        User near = persistUser("search-near@example.com", "Near Caretaker", UserTypeEnum.CARETAKER);
        User far = persistUser("search-far@example.com", "Far Caretaker", UserTypeEnum.CARETAKER);
        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(near))
                .contentType("application/json").content("{\"latitude\":12.9716,\"longitude\":77.5946}"));
        mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(loginCookieFor(far))
                .contentType("application/json").content("{\"latitude\":28.6139,\"longitude\":77.2090}"));
        User searcher = persistUser("search-coords-searcher@example.com", "Searcher", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/search/caretakers")
                        .param("latitude", "12.9716").param("longitude", "77.5946")
                        .cookie(loginCookieFor(searcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Near Caretaker"))
                .andExpect(jsonPath("$.data[0].distanceKm").value(org.hamcrest.Matchers.lessThan(1.0)));
    }

    @Test
    void searchingCaretakersWithNoQueryAndNoCoordinatesReturnsAnEmptyList() throws Exception {
        User searcher = persistUser("search-empty-searcher@example.com", "Searcher", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/search/caretakers").cookie(loginCookieFor(searcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void searchingElderProfilesIsScopedToTheCallersOwnProfilesWithRelationshipLabels() throws Exception {
        User owner = persistUser("search-ep-owner@example.com", "Owner Family", UserTypeEnum.FAMILY_MEMBER);
        User stranger = persistUser("search-ep-stranger@example.com", "Stranger Family", UserTypeEnum.FAMILY_MEMBER);
        Cookie ownerCookie = loginCookieFor(owner);
        MvcResult createResult = mockMvc.perform(post("/api/v1/elder-profiles").cookie(ownerCookie)
                        .contentType("application/json").content("{\"fullName\":\"Grandma Rose\"}"))
                .andReturn();
        objectMapper.readTree(createResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/search/elder-profiles").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Grandma Rose"))
                .andExpect(jsonPath("$.data[0].relationship").value("Owner"));

        mockMvc.perform(get("/api/v1/search/elder-profiles").cookie(loginCookieFor(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void searchingElderProfilesFiltersByQuery() throws Exception {
        User owner = persistUser("search-ep-filter-owner@example.com", "Owner Family", UserTypeEnum.FAMILY_MEMBER);
        Cookie ownerCookie = loginCookieFor(owner);
        mockMvc.perform(post("/api/v1/elder-profiles").cookie(ownerCookie)
                .contentType("application/json").content("{\"fullName\":\"Grandma Rose\"}"));
        mockMvc.perform(post("/api/v1/elder-profiles").cookie(ownerCookie)
                .contentType("application/json").content("{\"fullName\":\"Grandpa Joe\"}"));

        mockMvc.perform(get("/api/v1/search/elder-profiles").param("query", "rose").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Grandma Rose"));
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/caretakers").param("query", "x")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/search/elder-profiles")).andExpect(status().isUnauthorized());
    }
}
