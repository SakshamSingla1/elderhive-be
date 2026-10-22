package com.elderhive.integration;

import com.elderhive.entities.AuditLog;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.Notification;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.AuditLogRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.services.impl.NominatimGeocodingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 83) - confirms
 * the full trigger/fan-out flow: proximity matching genuinely excludes an out-of-radius
 * caretaker, every linked family member (the original owner AND a co-manager, not just
 * whichever one triggered it - the ElderSphere bug fix) gets notified, and an AuditLog
 * trail exists for the trigger plus every notification sent.
 *
 * <p>NominatimGeocodingClient is the one external-HTTP collaborator in this flow - mocked
 * here (this project's first use of @MockBean) so the test never makes a real network call
 * and stays deterministic/fast; trigger()'s own contract is "best-effort", so a stubbed
 * geocoder is a faithful stand-in for either a successful or a failed real lookup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmergencyAlertControllerIntegrationTest {

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
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NominatimGeocodingClient nominatimGeocodingClient;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private CaretakerProfile persistCaretaker(Long userId, double latitude, double longitude, double serviceRadiusKm,
                                               CaretakerVerificationStatusEnum status) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(status)
                .latitude(latitude).longitude(longitude).serviceRadiusKm(serviceRadiusKm).build());
    }

    private boolean hasNotification(Long userId, NotificationTypeEnum type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)).getContent().stream()
                .anyMatch(n -> n.getType() == type);
    }

    @Test
    void triggeringAnAlertNotifiesOnlyTheInRadiusCaretakerAndEveryLinkedFamilyMember() throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn("MG Road, Bengaluru");

        User familyOwner = persistUser("ea-trigger-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User coManagingFamily = persistUser("ea-trigger-comanager@example.com", UserTypeEnum.FAMILY_MEMBER);
        User unrelatedFamily = persistUser("ea-trigger-unrelated@example.com", UserTypeEnum.FAMILY_MEMBER);
        User nearCaretakerUser = persistUser("ea-trigger-near-caretaker@example.com", UserTypeEnum.CARETAKER);
        User farCaretakerUser = persistUser("ea-trigger-far-caretaker@example.com", UserTypeEnum.CARETAKER);

        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder()
                .familyUserId(familyOwner.getId()).fullName("Robert").build());
        familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(elder.getId()).familyUserId(coManagingFamily.getId()).relationshipLabel("Daughter").build());

        // Same longitude as the alert (0.0) - on a shared meridian the haversine distance
        // is exact: 0.01deg ~= 1.112km (well within its own 5km radius), 0.30deg ~= 33.358km
        // (well outside its own 10km radius).
        persistCaretaker(nearCaretakerUser.getId(), 0.01, 0.0, 5.0, CaretakerVerificationStatusEnum.VERIFIED);
        persistCaretaker(farCaretakerUser.getId(), 0.30, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);

        String body = objectMapper.writeValueAsString(Map.of(
                "elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));

        MvcResult result = mockMvc.perform(post("/api/v1/emergency-alerts")
                        .cookie(loginCookieFor(familyOwner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.elderName").value("Robert"))
                .andExpect(jsonPath("$.data.address").value("MG Road, Bengaluru"))
                .andExpect(jsonPath("$.data.status").value("TRIGGERED"))
                .andReturn();

        Long alertId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();

        assertThat(hasNotification(nearCaretakerUser.getId(), NotificationTypeEnum.EMERGENCY)).isTrue();
        assertThat(hasNotification(farCaretakerUser.getId(), NotificationTypeEnum.EMERGENCY)).isFalse();
        assertThat(hasNotification(familyOwner.getId(), NotificationTypeEnum.EMERGENCY)).isTrue();
        assertThat(hasNotification(coManagingFamily.getId(), NotificationTypeEnum.EMERGENCY)).isTrue();
        assertThat(hasNotification(unrelatedFamily.getId(), NotificationTypeEnum.EMERGENCY)).isFalse();

        List<AuditLog> auditLogs = auditLogRepository.findAll().stream()
                .filter(log -> log.getEntityType() == EntityTypeEnum.EMERGENCY_ALERT && log.getEntityId().equals(alertId.toString()))
                .toList();
        assertThat(auditLogs).filteredOn(log -> log.getActionType() == ActionType.CREATE).hasSize(1);
        // One NOTIFY row per actually-notified recipient: 1 caretaker + 2 family members.
        assertThat(auditLogs).filteredOn(log -> log.getActionType() == ActionType.NOTIFY).hasSize(3);
    }

    @Test
    void anUnrelatedFamilyMemberCannotTriggerAnAlertForSomeoneElsesElder() throws Exception {
        User familyOwner = persistUser("ea-forbidden-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User unrelatedFamily = persistUser("ea-forbidden-unrelated@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder()
                .familyUserId(familyOwner.getId()).fullName("Robert").build());

        String body = objectMapper.writeValueAsString(Map.of(
                "elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));

        mockMvc.perform(post("/api/v1/emergency-alerts")
                        .cookie(loginCookieFor(unrelatedFamily))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void anElderCanTriggerTheirOwnAlert() throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);

        User familyOwner = persistUser("ea-elderself-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User elderUser = persistUser("ea-elderself-elder@example.com", UserTypeEnum.ELDER);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder()
                .familyUserId(familyOwner.getId()).elderUserId(elderUser.getId()).fullName("Robert").build());

        String body = objectMapper.writeValueAsString(Map.of(
                "elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));

        mockMvc.perform(post("/api/v1/emergency-alerts")
                        .cookie(loginCookieFor(elderUser))
                        .contentType("application/json").content(body))
                // A failed/unresolved reverse geocode degrades to a null address, not a
                // thrown error - the trigger still succeeds.
                .andExpect(status().isCreated());

        assertThat(hasNotification(familyOwner.getId(), NotificationTypeEnum.EMERGENCY)).isTrue();
    }
}
