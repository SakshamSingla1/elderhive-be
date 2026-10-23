package com.elderhive.integration;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
import com.elderhive.entities.Role;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.EmergencyAlertRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 94) - builds every
 * fixture via direct repository saves rather than the full REST flow for each one (booking
 * creation's own availability-slot validation, emergency-alert's reverse-geocode call, etc.
 * are already thoroughly covered by their own controllers' integration tests; this one's job
 * is only to prove the dashboard's own counts are correct against real rows and that
 * DASHBOARD_VIEW actually gates the endpoint).
 *
 * <p>Asserts DELTAS (before vs. after this test's own fixture) rather than absolute counts -
 * this endpoint is deliberately global/unscoped (that's the whole point of a dashboard), so
 * it also reflects any earlier prompt's own live-verification data left in this shared dev
 * database on purpose (see e.g. Prompt 87/90's own "spot-check" notes); a delta assertion
 * proves the real behavior under test (does creating these rows move these counts by exactly
 * this much) without being fragile against however much unrelated data already exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardControllerIntegrationTest {

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
    private CaretakerProfileRepository caretakerProfileRepository;
    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;
    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private EmergencyAlertRepository emergencyAlertRepository;
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

    // AdminBootstrapSeeder (Prompt 24) seeds a curated "Admin" role with DASHBOARD_VIEW
    // (among every other non-Super-Admin-only permission) on every boot - same precedent
    // BookingControllerIntegrationTest's own adminOverrideStatus tests use.
    private User persistAdminWithDashboardView(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void aPlainFamilyMemberIsForbidden() throws Exception {
        User family = persistUser("dashboard-forbidden-family@example.com", UserTypeEnum.FAMILY_MEMBER);

        mockMvc.perform(get("/api/v1/dashboard/stats").cookie(loginCookieFor(family)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminWithDashboardViewSeesRealCountsAcrossEveryEntity() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate wellOutsideThisWeek = weekStart.minusMonths(2);

        // Baselines - read via repository, directly against the same tables/predicates the
        // dashboard itself queries, so this test is robust to however much unrelated data
        // (including earlier prompts' own live-verification leftovers) already exists.
        long baselineFamily = userRepository.countByUserTypeAndStatus(UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE);
        long baselineCaretaker = userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE);
        long baselineAdmin = userRepository.countByUserTypeAndStatus(UserTypeEnum.ADMIN, UserStatusEnum.ACTIVE);
        long baselineBookingsThisWeek = bookingRepository.countByScheduledDateBetween(weekStart, weekEnd);
        long baselineOpenAlerts = emergencyAlertRepository.countByStatus(EmergencyAlertStatusEnum.TRIGGERED)
                + emergencyAlertRepository.countByStatus(EmergencyAlertStatusEnum.ACKNOWLEDGED);
        long baselinePendingVerifications = caretakerProfileRepository.countByVerificationStatus(CaretakerVerificationStatusEnum.PENDING);

        User admin = persistAdminWithDashboardView("dashboard-admin@example.com");
        User familyUser = persistUser("dashboard-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User pendingCaretakerUser = persistUser("dashboard-pending-caretaker@example.com", UserTypeEnum.CARETAKER);
        User verifiedCaretakerUser = persistUser("dashboard-verified-caretaker@example.com", UserTypeEnum.CARETAKER);

        caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(pendingCaretakerUser.getId()).verificationStatus(CaretakerVerificationStatusEnum.PENDING).ratingAverage(0.0).build());
        CaretakerProfile verifiedCaretaker = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(verifiedCaretakerUser.getId()).verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).ratingAverage(0.0).build());

        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUser.getId()).fullName("Robert").build());
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name("dashboard-test-category").status(ServiceCategoryStatusEnum.ACTIVE).build());
        ServiceOffering offering = serviceOfferingRepository.save(ServiceOffering.builder()
                .name("dashboard-test-offering").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());

        bookingRepository.save(Booking.builder()
                .familyUserId(familyUser.getId()).elderProfileId(elder.getId()).caretakerId(verifiedCaretaker.getId())
                .serviceOfferingId(offering.getId()).scheduledDate(today).status(BookingStatusEnum.PENDING).build());
        bookingRepository.save(Booking.builder()
                .familyUserId(familyUser.getId()).elderProfileId(elder.getId()).caretakerId(verifiedCaretaker.getId())
                .serviceOfferingId(offering.getId()).scheduledDate(wellOutsideThisWeek).status(BookingStatusEnum.PENDING).build());

        emergencyAlertRepository.save(EmergencyAlert.builder()
                .elderProfileId(elder.getId()).triggeredByUserId(familyUser.getId())
                .latitude(0.0).longitude(0.0).status(EmergencyAlertStatusEnum.TRIGGERED).build());
        emergencyAlertRepository.save(EmergencyAlert.builder()
                .elderProfileId(elder.getId()).triggeredByUserId(familyUser.getId())
                .latitude(0.0).longitude(0.0).status(EmergencyAlertStatusEnum.RESOLVED).build());

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andReturn();
        var data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        var activeUsersByRole = data.get("activeUsersByRole");

        assertThat(activeUsersByRole.get("ADMIN").asLong()).isEqualTo(baselineAdmin + 1);
        assertThat(activeUsersByRole.get("FAMILY_MEMBER").asLong()).isEqualTo(baselineFamily + 1);
        assertThat(activeUsersByRole.get("CARETAKER").asLong()).isEqualTo(baselineCaretaker + 2);
        assertThat(data.get("bookingsThisWeek").asLong()).isEqualTo(baselineBookingsThisWeek + 1);
        assertThat(data.get("openEmergencyAlerts").asLong()).isEqualTo(baselineOpenAlerts + 1);
        assertThat(data.get("pendingCaretakerVerifications").asLong()).isEqualTo(baselinePendingVerifications + 1);
    }
}
