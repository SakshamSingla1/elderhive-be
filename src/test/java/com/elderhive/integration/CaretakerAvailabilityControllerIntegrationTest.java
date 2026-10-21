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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaretakerAvailabilityControllerIntegrationTest {

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

    private User persistCaretaker(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Test " + email).passwordHash("hashed")
                .userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private String slotJson(String day, String start, String end) {
        return """
                { "dayOfWeek": "%s", "startTime": "%s", "endTime": "%s" }
                """.formatted(day, start, end);
    }

    @Test
    void aCaretakerCanAddListUpdateAndDeleteTheirOwnAvailabilitySlots() throws Exception {
        User caretaker = persistCaretaker("ca-crud@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(cookie);

        MvcResult addResult = mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(cookie)
                        .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caretakerId").value(profileId))
                .andExpect(jsonPath("$.data.dayOfWeek").value("MONDAY"))
                .andReturn();
        Long slotId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/availability").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(put("/api/v1/caretaker-profiles/me/availability/" + slotId).cookie(cookie)
                        .contentType("application/json").content(slotJson("TUESDAY", "10:00:00", "18:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.data.startTime").value("10:00:00"));

        mockMvc.perform(delete("/api/v1/caretaker-profiles/me/availability/" + slotId).cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/availability").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void anyAuthenticatedUserCanViewACaretakersAvailability() throws Exception {
        User caretaker = persistCaretaker("ca-view-caretaker@example.com");
        User family = persistCaretaker("ca-view-family@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(cookie);
        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(cookie)
                .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")));

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/availability").cookie(loginCookieFor(family)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void addingASlotWithNoCaretakerProfileYetReturns404() throws Exception {
        User caretaker = persistCaretaker("ca-noprofile@example.com");

        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(loginCookieFor(caretaker))
                        .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingASlotWithStartTimeNotBeforeEndTimeReturns400() throws Exception {
        User caretaker = persistCaretaker("ca-badrange@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        createCaretakerProfile(cookie);

        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(cookie)
                        .contentType("application/json").content(slotJson("MONDAY", "17:00:00", "09:00:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingASlotWithMissingFieldsFailsValidation() throws Exception {
        User caretaker = persistCaretaker("ca-validation@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        createCaretakerProfile(cookie);

        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(cookie)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStrangerCannotUpdateOrDeleteAnotherCaretakersSlot() throws Exception {
        User caretaker = persistCaretaker("ca-stranger-owner@example.com");
        User stranger = persistCaretaker("ca-stranger-stranger@example.com");
        Cookie ownerCookie = loginCookieFor(caretaker);
        createCaretakerProfile(ownerCookie);
        MvcResult addResult = mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(ownerCookie)
                        .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")))
                .andReturn();
        Long slotId = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        createCaretakerProfile(loginCookieFor(stranger));

        mockMvc.perform(put("/api/v1/caretaker-profiles/me/availability/" + slotId).cookie(loginCookieFor(stranger))
                        .contentType("application/json").content(slotJson("TUESDAY", "10:00:00", "18:00:00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/caretaker-profiles/me/availability/" + slotId).cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingOrDeletingAnUnknownSlotReturns404() throws Exception {
        User caretaker = persistCaretaker("ca-404@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        createCaretakerProfile(cookie);

        mockMvc.perform(put("/api/v1/caretaker-profiles/me/availability/999999999").cookie(cookie)
                        .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/caretaker-profiles/me/availability/999999999").cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/caretaker-profiles/1/availability")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability")
                        .contentType("application/json").content(slotJson("MONDAY", "09:00:00", "17:00:00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gettingAvailabilityForAnUnknownCaretakerReturns404() throws Exception {
        User caretaker = persistCaretaker("ca-unknown-caretaker@example.com");

        mockMvc.perform(get("/api/v1/caretaker-profiles/999999999/availability").cookie(loginCookieFor(caretaker)))
                .andExpect(status().isNotFound());
    }
}
