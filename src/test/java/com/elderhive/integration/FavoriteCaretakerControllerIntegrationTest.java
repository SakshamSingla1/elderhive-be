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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class FavoriteCaretakerControllerIntegrationTest {

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

    private User persistUser(String email, UserTypeEnum type) {
        return userRepository.save(User.builder()
                .email(email).fullName("Test " + email).passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    void aFamilyMemberCanAddListAndRemoveAFavoriteCaretaker() throws Exception {
        User family = persistUser("fc-crud-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-crud-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie familyCookie = loginCookieFor(family);
        Long caretakerId = createCaretakerProfile(loginCookieFor(caretakerUser));

        mockMvc.perform(post("/api/v1/favorites/" + caretakerId).cookie(familyCookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(caretakerId));

        mockMvc.perform(get("/api/v1/favorites").cookie(familyCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(caretakerId));

        mockMvc.perform(delete("/api/v1/favorites/" + caretakerId).cookie(familyCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/favorites").cookie(familyCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void addingAnUnknownCaretakerReturns404() throws Exception {
        User family = persistUser("fc-404-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/favorites/999999999").cookie(loginCookieFor(family)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingTheSameCaretakerTwiceReturns409() throws Exception {
        User family = persistUser("fc-conflict-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-conflict-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie familyCookie = loginCookieFor(family);
        Long caretakerId = createCaretakerProfile(loginCookieFor(caretakerUser));

        mockMvc.perform(post("/api/v1/favorites/" + caretakerId).cookie(familyCookie)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/favorites/" + caretakerId).cookie(familyCookie))
                .andExpect(status().isConflict());
    }

    @Test
    void removingACaretakerThatWasNeverFavoritedReturns404() throws Exception {
        User family = persistUser("fc-remove404-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-remove404-caretaker@example.com", UserTypeEnum.CARETAKER);
        Long caretakerId = createCaretakerProfile(loginCookieFor(caretakerUser));

        mockMvc.perform(delete("/api/v1/favorites/" + caretakerId).cookie(loginCookieFor(family)))
                .andExpect(status().isNotFound());
    }

    @Test
    void favoritesAreScopedPerCallerNotSharedAcrossUsers() throws Exception {
        User family1 = persistUser("fc-scope-family1@example.com", UserTypeEnum.FAMILY_MEMBER);
        User family2 = persistUser("fc-scope-family2@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("fc-scope-caretaker@example.com", UserTypeEnum.CARETAKER);
        Long caretakerId = createCaretakerProfile(loginCookieFor(caretakerUser));

        mockMvc.perform(post("/api/v1/favorites/" + caretakerId).cookie(loginCookieFor(family1)));

        mockMvc.perform(get("/api/v1/favorites").cookie(loginCookieFor(family2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/favorites").cookie(loginCookieFor(family1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/favorites/1")).andExpect(status().isUnauthorized());
    }
}
