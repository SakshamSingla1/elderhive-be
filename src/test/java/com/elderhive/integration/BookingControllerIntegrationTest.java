package com.elderhive.integration;

import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.Role;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.RoleRepository;
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
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack - confirms the full
 * booking lifecycle (Prompt 77): create (re-validating the slot server-side against
 * Prompt 76's AvailableSlotService), confirm/start/complete (caretaker-only), cancel
 * (family-or-caretaker depending on status, admin override), and the recurring-series
 * create/view/cancel path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ElderProfileRepository elderProfileRepository;

    @Autowired
    private FamilyElderLinkRepository familyElderLinkRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // The next Monday from "today" - deterministic across runs without hardcoding a
    // calendar date that will eventually look stale (same convention as
    // AvailableSlotControllerIntegrationTest).
    private static final LocalDate MONDAY = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private User persistAdmin(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private void addAvailability(Cookie caretakerCookie, String day, String start, String end) throws Exception {
        mockMvc.perform(post("/api/v1/caretaker-profiles/me/availability").cookie(caretakerCookie)
                .contentType("application/json")
                .content("{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(day, start, end)));
    }

    private Long persistServiceOffering(String namePrefix, Integer durationMinutes) {
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        return serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId())
                .basePrice(new java.math.BigDecimal("40.00")).durationMinutes(durationMinutes).status(ServiceOfferingStatusEnum.ACTIVE).build())
                .getId();
    }

    private String bookingJson(Long elderProfileId, Long caretakerId, Long serviceOfferingId, String time) {
        return """
                {"elderProfileId":%d,"caretakerId":%d,"serviceOfferingId":%d,"scheduledDate":"%s","scheduledTime":"%s"}
                """.formatted(elderProfileId, caretakerId, serviceOfferingId, MONDAY, time);
    }

    /** Bundles a ready-to-book fixture: an available caretaker + offering + elder profile
     *  owned by the returned family cookie. */
    private record Fixture(Cookie familyCookie, Long elderProfileId, Cookie caretakerCookie, Long caretakerId, Long offeringId) {
    }

    private Fixture setUpBookableFixture(String prefix) throws Exception {
        User family = persistUser(prefix + "-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Robert").build());

        User caretakerUser = persistUser(prefix + "-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretakerUser);
        Long caretakerId = createCaretakerProfile(caretakerCookie);
        addAvailability(caretakerCookie, "MONDAY", "09:00:00", "12:00:00");

        Long offeringId = persistServiceOffering(prefix, 60);
        return new Fixture(familyCookie, elder.getId(), caretakerCookie, caretakerId, offeringId);
    }

    @Test
    void theFullLifecycleFromCreateThroughConfirmStartAndCompleteSucceeds() throws Exception {
        Fixture f = setUpBookableFixture("lifecycle");

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.cost").value(40.00))
                .andExpect(jsonPath("$.data.elderName").value("Robert"))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(f.caretakerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/v1/bookings/" + id + "/start").cookie(f.caretakerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(put("/api/v1/bookings/" + id + "/complete").cookie(f.caretakerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/bookings/" + id).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void creatingABookingAtATimeOutsideAvailabilityReturns409() throws Exception {
        Fixture f = setUpBookableFixture("unavail");

        mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "15:00:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingABookingForAnElderProfileTheCallerHasNoRelationToReturns403() throws Exception {
        Fixture f = setUpBookableFixture("stranger");
        User stranger = persistUser("stranger-uninvolved@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/bookings").cookie(loginCookieFor(stranger))
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCoManagingFamilyMemberCanAlsoCreateABooking() throws Exception {
        Fixture f = setUpBookableFixture("comanager");
        User coManager = persistUser("comanager-family2@example.com", UserTypeEnum.FAMILY_MEMBER);
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(f.elderProfileId()).familyUserId(coManager.getId()).build());

        mockMvc.perform(post("/api/v1/bookings").cookie(loginCookieFor(coManager))
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmingSomeoneElsesBookingReturns403() throws Exception {
        Fixture f = setUpBookableFixture("wrongcaretaker");
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        User otherCaretakerUser = persistUser("wrongcaretaker-other@example.com", UserTypeEnum.CARETAKER);
        Cookie otherCaretakerCookie = loginCookieFor(otherCaretakerUser);
        createCaretakerProfile(otherCaretakerCookie);

        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(otherCaretakerCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmingAnAlreadyConfirmedBookingReturns400() throws Exception {
        Fixture f = setUpBookableFixture("doubleconfirm");
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(f.caretakerCookie()));

        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(f.caretakerCookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellingAPendingBookingIsAllowedForEitherPartyButNotOnceConfirmed() throws Exception {
        Fixture f = setUpBookableFixture("cancelrules");
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Caretaker can cancel a still-PENDING booking.
        mockMvc.perform(put("/api/v1/bookings/" + id + "/cancel").cookie(f.caretakerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // A second booking, this time confirmed - the caretaker may no longer cancel it.
        MvcResult secondCreate = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "10:00:00")))
                .andReturn();
        Long secondId = objectMapper.readTree(secondCreate.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(put("/api/v1/bookings/" + secondId + "/confirm").cookie(f.caretakerCookie()));

        mockMvc.perform(put("/api/v1/bookings/" + secondId + "/cancel").cookie(f.caretakerCookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/bookings/" + secondId + "/cancel").cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void anAdminCanCancelAConfirmedBookingEvenThoughTheCaretakerCannot() throws Exception {
        Fixture f = setUpBookableFixture("adminoverride");
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(f.caretakerCookie()));
        Cookie adminCookie = loginCookieFor(persistAdmin("adminoverride-admin@example.com"));

        mockMvc.perform(put("/api/v1/bookings/" + id + "/cancel").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void creatingARecurringBookingMakesOneRowPerOccurrenceSharingAGroupIdAndCancelSeriesCancelsAll() throws Exception {
        Fixture f = setUpBookableFixture("recurring");

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content("""
                                {"elderProfileId":%d,"caretakerId":%d,"serviceOfferingId":%d,"scheduledDate":"%s","scheduledTime":"09:00:00","recurring":true,"occurrences":3}
                                """.formatted(f.elderProfileId(), f.caretakerId(), f.offeringId(), MONDAY))
                        .accept("application/json"))
                .andExpect(status().isCreated())
                .andReturn();
        var data = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        String groupId = data.get("recurringGroupId").asText();
        org.assertj.core.api.Assertions.assertThat(groupId).isNotBlank();

        mockMvc.perform(get("/api/v1/bookings/recurring-group/" + groupId).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        Long firstId = data.get("id").asLong();
        mockMvc.perform(put("/api/v1/bookings/" + firstId + "/cancel").cookie(f.familyCookie()).param("cancelSeries", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/recurring-group/" + groupId).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[2].status").value("CANCELLED"));
    }

    @Test
    void recurringBookingWithOutOfRangeOccurrencesFailsValidation() throws Exception {
        Fixture f = setUpBookableFixture("badoccurrences");

        mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content("""
                                {"elderProfileId":%d,"caretakerId":%d,"serviceOfferingId":%d,"scheduledDate":"%s","scheduledTime":"09:00:00","recurring":true,"occurrences":1}
                                """.formatted(f.elderProfileId(), f.caretakerId(), f.offeringId(), MONDAY)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyBookingsAsFamilyAndAsCaretakerReturnTheCallersOwnBookings() throws Exception {
        Fixture f = setUpBookableFixture("mybookings");
        mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                .contentType("application/json")
                .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")));

        mockMvc.perform(get("/api/v1/bookings/me/as-family").cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/bookings/me/as-caretaker").cookie(f.caretakerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void gettingABookingAsAnUninvolvedUserReturns403() throws Exception {
        Fixture f = setUpBookableFixture("viewforbidden");
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content(bookingJson(f.elderProfileId(), f.caretakerId(), f.offeringId(), "09:00:00")))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        User stranger = persistUser("viewforbidden-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/bookings/" + id).cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gettingAnUnknownBookingReturns404() throws Exception {
        User family = persistUser("unknownbooking-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/bookings/999999999").cookie(loginCookieFor(family)))
                .andExpect(status().isNotFound());
    }
}
