package com.elderhive.integration;

import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
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
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 88) - drives a
 * booking through its real lifecycle to COMPLETED (same fixture shape as
 * BookingControllerIntegrationTest) before exercising review creation, the family-party
 * authorization check, the COMPLETED-only and one-review-per-booking rules, listing,
 * lookup-by-booking, and the caretaker reply upsert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

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

    private record Fixture(Cookie familyCookie, Long familyUserId, Long elderProfileId, Cookie caretakerCookie, Long caretakerUserId, Long caretakerId) {
    }

    /** Creates a family + elder + caretaker, books a slot, and drives the booking all the
     *  way to COMPLETED - the only state a review can be posted against. */
    private Fixture setUpCompletedBookingFixture(String prefix) throws Exception {
        User family = persistUser(prefix + "-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(family.getId()).fullName("Robert").build());

        User caretakerUser = persistUser(prefix + "-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie caretakerCookie = loginCookieFor(caretakerUser);
        Long caretakerId = createCaretakerProfile(caretakerCookie);
        addAvailability(caretakerCookie, "MONDAY", "09:00:00", "12:00:00");

        return new Fixture(familyCookie, family.getId(), elder.getId(), caretakerCookie, caretakerUser.getId(), caretakerId);
    }

    private Long createAndCompleteBooking(Fixture f, Long offeringId, String time) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content("""
                                {"elderProfileId":%d,"caretakerId":%d,"serviceOfferingId":%d,"scheduledDate":"%s","scheduledTime":"%s"}
                                """.formatted(f.elderProfileId(), f.caretakerId(), offeringId, MONDAY, time)))
                .andReturn();
        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/bookings/" + id + "/confirm").cookie(f.caretakerCookie()));
        mockMvc.perform(put("/api/v1/bookings/" + id + "/start").cookie(f.caretakerCookie()));
        mockMvc.perform(put("/api/v1/bookings/" + id + "/complete").cookie(f.caretakerCookie()));
        return id;
    }

    private String reviewJson(Long bookingId, Integer overallRating) {
        return """
                {"bookingId":%d,"overallRating":%d,"comment":"Wonderful care"}
                """.formatted(bookingId, overallRating);
    }

    @Test
    void theFamilyOwnerCanReviewACompletedBookingAndTheCaretakersRatingAverageUpdates() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-happy");
        Long offeringId = persistServiceOffering("review-happy", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");

        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                        .contentType("application/json").content(reviewJson(bookingId, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.overallRating").value(5))
                .andExpect(jsonPath("$.data.punctualityRating").value(5))
                .andExpect(jsonPath("$.data.caretakerId").value(f.caretakerId()));

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + f.caretakerId()).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingAverage").value(5.0));
    }

    @Test
    void aFamilyElderLinkCoManagerCanAlsoReviewABookingTheyDidNotPersonallyBook() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-comanager");
        Long offeringId = persistServiceOffering("review-comanager", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        User coManager = persistUser("review-comanager-family2@example.com", UserTypeEnum.FAMILY_MEMBER);
        familyElderLinkRepository.save(FamilyElderLink.builder().elderProfileId(f.elderProfileId()).familyUserId(coManager.getId()).build());

        mockMvc.perform(post("/api/v1/reviews").cookie(loginCookieFor(coManager))
                        .contentType("application/json").content(reviewJson(bookingId, 4)))
                .andExpect(status().isCreated());
    }

    @Test
    void anUnrelatedFamilyMemberCannotReviewSomeoneElsesBookingAndGets403() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-stranger");
        Long offeringId = persistServiceOffering("review-stranger", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        User stranger = persistUser("review-stranger-other@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(post("/api/v1/reviews").cookie(loginCookieFor(stranger))
                        .contentType("application/json").content(reviewJson(bookingId, 5)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewingABookingThatIsNotYetCompletedReturns400() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-notcompleted");
        Long offeringId = persistServiceOffering("review-notcompleted", 60);
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings").cookie(f.familyCookie())
                        .contentType("application/json")
                        .content("""
                                {"elderProfileId":%d,"caretakerId":%d,"serviceOfferingId":%d,"scheduledDate":"%s","scheduledTime":"09:00:00"}
                                """.formatted(f.elderProfileId(), f.caretakerId(), offeringId, MONDAY)))
                .andReturn();
        Long bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                        .contentType("application/json").content(reviewJson(bookingId, 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewingTheSameBookingTwiceReturns409() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-duplicate");
        Long offeringId = persistServiceOffering("review-duplicate", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                .contentType("application/json").content(reviewJson(bookingId, 5)));

        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                        .contentType("application/json").content(reviewJson(bookingId, 3)))
                .andExpect(status().isConflict());
    }

    @Test
    void getByCaretakerListsEveryReviewTheyHaveReceived() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-list");
        Long offeringId = persistServiceOffering("review-list", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                .contentType("application/json").content(reviewJson(bookingId, 5)));

        mockMvc.perform(get("/api/v1/reviews/caretaker/" + f.caretakerId()).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].overallRating").value(5));
    }

    @Test
    void getByBookingIdReturns404BeforeAReviewExistsAnd200AfterOneIsPosted() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-bybooking");
        Long offeringId = persistServiceOffering("review-bybooking", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");

        mockMvc.perform(get("/api/v1/reviews/booking/" + bookingId).cookie(f.familyCookie()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                .contentType("application/json").content(reviewJson(bookingId, 4)));

        mockMvc.perform(get("/api/v1/reviews/booking/" + bookingId).cookie(f.familyCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(4));
    }

    @Test
    void theReviewedCaretakerCanPostThenUpdateTheirReplyInPlace() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-reply");
        Long offeringId = persistServiceOffering("review-reply", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        MvcResult reviewResult = mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                        .contentType("application/json").content(reviewJson(bookingId, 5)))
                .andReturn();
        Long reviewId = objectMapper.readTree(reviewResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/reviews/" + reviewId + "/reply").cookie(f.caretakerCookie())
                        .contentType("application/json").content("{\"content\":\"Thank you!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Thank you!"));

        mockMvc.perform(put("/api/v1/reviews/" + reviewId + "/reply").cookie(f.caretakerCookie())
                        .contentType("application/json").content("{\"content\":\"Thank you so much!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Thank you so much!"));

        mockMvc.perform(get("/api/v1/reviews/booking/" + bookingId).cookie(f.familyCookie()))
                .andExpect(jsonPath("$.data.reply.content").value("Thank you so much!"));
    }

    @Test
    void aDifferentCaretakerCannotReplyToSomeoneElsesReviewAndGets403() throws Exception {
        Fixture f = setUpCompletedBookingFixture("review-wrongcaretaker");
        Long offeringId = persistServiceOffering("review-wrongcaretaker", 60);
        Long bookingId = createAndCompleteBooking(f, offeringId, "09:00:00");
        MvcResult reviewResult = mockMvc.perform(post("/api/v1/reviews").cookie(f.familyCookie())
                        .contentType("application/json").content(reviewJson(bookingId, 5)))
                .andReturn();
        Long reviewId = objectMapper.readTree(reviewResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        User otherCaretakerUser = persistUser("review-wrongcaretaker-other@example.com", UserTypeEnum.CARETAKER);
        Cookie otherCaretakerCookie = loginCookieFor(otherCaretakerUser);
        createCaretakerProfile(otherCaretakerCookie);

        mockMvc.perform(put("/api/v1/reviews/" + reviewId + "/reply").cookie(otherCaretakerCookie)
                        .contentType("application/json").content("{\"content\":\"Not mine\"}"))
                .andExpect(status().isForbidden());
    }
}
