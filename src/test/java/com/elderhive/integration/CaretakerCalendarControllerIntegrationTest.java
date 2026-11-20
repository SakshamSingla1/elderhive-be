package com.elderhive.integration;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.ServiceCategoryRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack - the point of this
 * suite is specifically proving GET /me/calendar.ics is reachable with NO accessToken
 * cookie at all (SecurityConfig's permitAll, Prompt 106), unlike every other "me" route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaretakerCalendarControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;
    @Autowired
    private ElderProfileRepository elderProfileRepository;
    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;
    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistCaretaker(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private void persistConfirmedBooking(Long caretakerId, String elderName, String serviceName, int durationMinutes) {
        User family = userRepository.save(User.builder()
                .email(elderName.toLowerCase().replace(" ", "-") + "-family@example.com")
                .passwordHash("hashed").userType(UserTypeEnum.FAMILY_MEMBER).status(UserStatusEnum.ACTIVE).build());
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName(elderName).build());
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder().name("Category").status(ServiceCategoryStatusEnum.ACTIVE).build());
        ServiceOffering offering = serviceOfferingRepository.save(ServiceOffering.builder()
                .name(serviceName).categoryId(category.getId()).durationMinutes(durationMinutes).status(ServiceOfferingStatusEnum.ACTIVE).build());
        bookingRepository.save(Booking.builder()
                .familyUserId(family.getId()).elderProfileId(elder.getId()).caretakerId(caretakerId).serviceOfferingId(offering.getId())
                .scheduledDate(LocalDate.now().plusDays(3)).scheduledTime(LocalTime.of(14, 30))
                .status(BookingStatusEnum.CONFIRMED).build());
    }

    @Test
    void aCaretakerCanFetchAndRegenerateTheirOwnCalendarLink() throws Exception {
        User caretaker = persistCaretaker("calendar-link@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        createCaretakerProfile(cookie);

        MvcResult firstFetch = mockMvc.perform(get("/api/v1/caretakers/me/calendar-link").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        String firstUrl = objectMapper.readTree(firstFetch.getResponse().getContentAsString()).get("data").get("icsUrl").asText();
        assertUrlContainsToken(firstUrl);

        // Fetching again returns the SAME url - the token doesn't change on every read.
        MvcResult secondFetch = mockMvc.perform(get("/api/v1/caretakers/me/calendar-link").cookie(cookie)).andReturn();
        String secondUrl = objectMapper.readTree(secondFetch.getResponse().getContentAsString()).get("data").get("icsUrl").asText();
        org.assertj.core.api.Assertions.assertThat(secondUrl).isEqualTo(firstUrl);

        MvcResult regenerated = mockMvc.perform(post("/api/v1/caretakers/me/calendar-link/regenerate").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        String regeneratedUrl = objectMapper.readTree(regenerated.getResponse().getContentAsString()).get("data").get("icsUrl").asText();
        org.assertj.core.api.Assertions.assertThat(regeneratedUrl).isNotEqualTo(firstUrl);

        // The OLD link is now dead - a leaked URL is invalidated by regenerating.
        String oldToken = firstUrl.substring(firstUrl.indexOf("token=") + 6);
        mockMvc.perform(get("/api/v1/caretakers/me/calendar.ics").param("token", oldToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void theIcsFeedIsReachableWithNoAccessTokenCookieAtAllAndListsUpcomingConfirmedBookings() throws Exception {
        User caretaker = persistCaretaker("calendar-public@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        Long caretakerId = createCaretakerProfile(cookie);
        persistConfirmedBooking(caretakerId, "Grandma Rose", "Companionship Visit", 90);

        MvcResult linkResult = mockMvc.perform(get("/api/v1/caretakers/me/calendar-link").cookie(cookie)).andReturn();
        String icsUrl = objectMapper.readTree(linkResult.getResponse().getContentAsString()).get("data").get("icsUrl").asText();
        String token = icsUrl.substring(icsUrl.indexOf("token=") + 6);

        // Deliberately NO .cookie(...) here - this is the whole point of the test.
        mockMvc.perform(get("/api/v1/caretakers/me/calendar.ics").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SUMMARY:Companionship Visit with Grandma Rose")));
    }

    @Test
    void anUnknownTokenReturnsNotFoundRatherThanLeakingAnyCaretakersSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/caretakers/me/calendar.ics").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void calendarLinkEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/caretakers/me/calendar-link")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/caretakers/me/calendar-link/regenerate")).andExpect(status().isUnauthorized());
    }

    private void assertUrlContainsToken(String url) {
        org.assertj.core.api.Assertions.assertThat(url).contains("/api/v1/caretakers/me/calendar.ics?token=");
    }
}
