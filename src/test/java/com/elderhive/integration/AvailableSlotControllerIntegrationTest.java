package com.elderhive.integration;

import com.elderhive.entities.Booking;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack - confirms
 * AvailableSlotServiceImpl's slot computation (Prompt 76) through the actual
 * GET /caretaker-profiles/{id}/available-slots endpoint, including the default-hours
 * fallback and subtracting a real Booking row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvailableSlotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

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

    // The next Monday from "today" - deterministic across runs without hardcoding a
    // calendar date that will eventually look stale.
    private static final LocalDate MONDAY = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private Long persistServiceOffering(String namePrefix, Integer durationMinutes) {
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        return serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId())
                .durationMinutes(durationMinutes).status(ServiceOfferingStatusEnum.ACTIVE).build()).getId();
    }

    private void addAvailability(Cookie caretakerCookie, String day, String start, String end) throws Exception {
        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(caretakerCookie)
                .contentType("application/json")
                .content("{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(day, start, end)));
    }

    @Test
    void fallsBackToDefaultHoursWhenTheCaretakerHasNoCustomAvailability() throws Exception {
        User caretaker = persistUser("slots-default-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        Long offeringId = persistServiceOffering("slots-default", 60);

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(caretakerCookie)
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usingDefaultHours").value(true))
                .andExpect(jsonPath("$.data.slotDurationMinutes").value(60))
                .andExpect(jsonPath("$.data.availableSlots[0]").value("09:00:00"))
                .andExpect(jsonPath("$.data.availableSlots[8]").value("17:00:00"))
                .andExpect(jsonPath("$.data.availableSlots.length()").value(9));
    }

    @Test
    void usesTheCaretakersOwnScheduleWhenSet() throws Exception {
        User caretaker = persistUser("slots-custom-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        Long offeringId = persistServiceOffering("slots-custom", 60);
        addAvailability(caretakerCookie, MONDAY.getDayOfWeek().name(), "09:00:00", "12:00:00");

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(caretakerCookie)
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usingDefaultHours").value(false))
                .andExpect(jsonPath("$.data.availableSlots", org.hamcrest.Matchers.contains("09:00:00", "10:00:00", "11:00:00")));
    }

    @Test
    void excludesASlotAlreadyOccupiedByAConfirmedBooking() throws Exception {
        User caretaker = persistUser("slots-booked-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        Long offeringId = persistServiceOffering("slots-booked", 60);
        addAvailability(caretakerCookie, MONDAY.getDayOfWeek().name(), "09:00:00", "12:00:00");

        User family = persistUser("slots-booked-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Robert").build());
        bookingRepository.save(Booking.builder()
                .familyUserId(family.getId()).elderProfileId(elder.getId()).caretakerId(profileId).serviceOfferingId(offeringId)
                .scheduledDate(MONDAY).scheduledTime(LocalTime.of(10, 0)).status(BookingStatusEnum.CONFIRMED).build());

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(caretakerCookie)
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSlots", org.hamcrest.Matchers.contains("09:00:00", "11:00:00")));
    }

    @Test
    void stillCountsACancelledBookingsSlotAsFree() throws Exception {
        User caretaker = persistUser("slots-cancelled-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        Long offeringId = persistServiceOffering("slots-cancelled", 60);
        addAvailability(caretakerCookie, MONDAY.getDayOfWeek().name(), "09:00:00", "11:00:00");

        User family = persistUser("slots-cancelled-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Robert").build());
        bookingRepository.save(Booking.builder()
                .familyUserId(family.getId()).elderProfileId(elder.getId()).caretakerId(profileId).serviceOfferingId(offeringId)
                .scheduledDate(MONDAY).scheduledTime(LocalTime.of(9, 0)).status(BookingStatusEnum.CANCELLED).build());

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(caretakerCookie)
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSlots", org.hamcrest.Matchers.contains("09:00:00", "10:00:00")));
    }

    @Test
    void anyAuthenticatedUserCanViewSlotsNotJustTheCaretakerThemself() throws Exception {
        User caretaker = persistUser("slots-anyviewer-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        Long offeringId = persistServiceOffering("slots-anyviewer", 60);
        User family = persistUser("slots-anyviewer-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(loginCookieFor(family))
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownCaretakerReturns404() throws Exception {
        User family = persistUser("slots-nocaretaker-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Long offeringId = persistServiceOffering("slots-nocaretaker", 60);

        mockMvc.perform(get("/api/v1/caretaker-profiles/999999999/available-slots")
                        .cookie(loginCookieFor(family))
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", String.valueOf(offeringId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownServiceOfferingReturns404() throws Exception {
        User caretaker = persistUser("slots-nooffering-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/available-slots")
                        .cookie(caretakerCookie)
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/caretaker-profiles/1/available-slots")
                        .param("date", MONDAY.toString())
                        .param("serviceOfferingId", "1"))
                .andExpect(status().isUnauthorized());
    }
}
