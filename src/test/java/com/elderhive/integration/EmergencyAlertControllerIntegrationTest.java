package com.elderhive.integration;

import com.elderhive.entities.AuditLog;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.Notification;
import com.elderhive.entities.Role;
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
import com.elderhive.presence.PresenceTracker;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.security.WebSocketJwtHandshakeInterceptor;
import com.elderhive.services.impl.NominatimGeocodingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.messaging.SessionConnectEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NominatimGeocodingClient nominatimGeocodingClient;

    @Autowired
    private PresenceTracker presenceTracker;

    // Prompt 90 wired ProximityMatchingServiceImpl's own isOnline() check to the real
    // PresenceTracker (no more "assume everyone's online" stub) - a caretaker this test
    // expects to actually match an alert now has to have a live session, same as a real
    // connected client would via WebSocketJwtHandshakeInterceptor's own session attributes.
    private void markOnline(Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID, userId));
        presenceTracker.onConnect(new SessionConnectEvent(this, MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())));
    }

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

    // AdminBootstrapSeeder (Prompt 24) seeds a curated "Admin" role with EMERGENCY_DISPATCH
    // on every boot - the same precedent BookingControllerIntegrationTest's own
    // adminOverrideStatus tests use, rather than hand-rolling Role/Permission/RolePermission
    // rows here.
    private User persistAdminWithEmergencyDispatch(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
    }

    private Long triggerAlert(User owner, ElderProfile elder) throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        String body = objectMapper.writeValueAsString(Map.of(
                "elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));
        MvcResult result = mockMvc.perform(post("/api/v1/emergency-alerts")
                        .cookie(loginCookieFor(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
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
        markOnline(nearCaretakerUser.getId());

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

    @Test
    void firstClaimWinsAndASecondClaimAttemptIsRejected() throws Exception {
        User familyOwner = persistUser("ea-claim-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User firstCaretakerUser = persistUser("ea-claim-first@example.com", UserTypeEnum.CARETAKER);
        User secondCaretakerUser = persistUser("ea-claim-second@example.com", UserTypeEnum.CARETAKER);
        persistCaretaker(firstCaretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        persistCaretaker(secondCaretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        Long alertId = triggerAlert(familyOwner, elder);

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/claim").cookie(loginCookieFor(firstCaretakerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/claim").cookie(loginCookieFor(secondCaretakerUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void theClaimingCaretakerCanResolveButAnotherCaretakerCannot() throws Exception {
        User familyOwner = persistUser("ea-resolve-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User claimingCaretakerUser = persistUser("ea-resolve-claiming@example.com", UserTypeEnum.CARETAKER);
        User otherCaretakerUser = persistUser("ea-resolve-other@example.com", UserTypeEnum.CARETAKER);
        persistCaretaker(claimingCaretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        persistCaretaker(otherCaretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        Long alertId = triggerAlert(familyOwner, elder);
        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/claim").cookie(loginCookieFor(claimingCaretakerUser)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/resolve").cookie(loginCookieFor(otherCaretakerUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/resolve").cookie(loginCookieFor(claimingCaretakerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.responseTimeSeconds").isNumber());
    }

    @Test
    void anAdminWithEmergencyDispatchCanForceResolveAnUnclaimedAlertButAPlainAdminCannotEvenReachTheEndpoint() throws Exception {
        User familyOwner = persistUser("ea-override-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistAdminWithEmergencyDispatch("ea-override-admin@example.com");
        User plainAdmin = persistUser("ea-override-plain-admin@example.com", UserTypeEnum.ADMIN); // no roleId -> no permissions at all
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        Long alertId = triggerAlert(familyOwner, elder);

        String forceResolveBody = objectMapper.writeValueAsString(Map.of("forceResolve", true));

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/dispatch-override")
                        .cookie(loginCookieFor(plainAdmin)).contentType("application/json").content(forceResolveBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/dispatch-override")
                        .cookie(loginCookieFor(admin)).contentType("application/json").content(forceResolveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void dispatchOverrideCanReassignAnUnclaimedAlertToAChosenCaretaker() throws Exception {
        User familyOwner = persistUser("ea-reassign-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistAdminWithEmergencyDispatch("ea-reassign-admin@example.com");
        User caretakerUser = persistUser("ea-reassign-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretakerProfile = persistCaretaker(caretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        Long alertId = triggerAlert(familyOwner, elder);

        String reassignBody = objectMapper.writeValueAsString(Map.of("respondingCaretakerId", caretakerProfile.getId()));

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/dispatch-override")
                        .cookie(loginCookieFor(admin)).contentType("application/json").content(reassignBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.respondingCaretakerId").value(caretakerProfile.getId()));
    }

    @Test
    void aFreshlyTriggeredAlertIsNotOverdueAndTheJsonKeyIsLiterallyIsOverdue() throws Exception {
        User familyOwner = persistUser("ea-overdue-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        String body = objectMapper.writeValueAsString(Map.of("elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));

        // Asserts the literal JSON key "isOverdue" - the Lombok/Jackson double-"is"-
        // stripping gotcha this DTO's own @JsonProperty pin exists to prevent (see
        // EmergencyAlertResponse's own comment) only ever surfaces at real serialization
        // time, which a Mockito-based unit test never exercises.
        mockMvc.perform(post("/api/v1/emergency-alerts").cookie(loginCookieFor(familyOwner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isOverdue").value(false));
    }

    @Test
    void nearbyReturnsOnlyAlertsWithinRangeOfTheCallingCaretakerWithDistanceSetAndOrderedNearestFirst() throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        User familyOwner = persistUser("ea-nearby-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User nearCaretakerUser = persistUser("ea-nearby-near-caretaker@example.com", UserTypeEnum.CARETAKER);
        User farCaretakerUser = persistUser("ea-nearby-far-caretaker@example.com", UserTypeEnum.CARETAKER);
        persistCaretaker(nearCaretakerUser.getId(), 0.01, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        persistCaretaker(farCaretakerUser.getId(), 0.30, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        markOnline(nearCaretakerUser.getId());
        markOnline(farCaretakerUser.getId());
        ElderProfile elderA = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        ElderProfile elderB = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Grandma Rose").build());

        // Same longitude as both alerts (0.0) - on a shared meridian the haversine distance
        // is exact: 0.01deg ~= 1.112km from the near caretaker, 0.04deg ~= 4.448km.
        Long nearerAlertId = triggerAlert(familyOwner, elderA);
        String secondBody = objectMapper.writeValueAsString(Map.of("elderProfileId", elderB.getId(), "latitude", 0.04, "longitude", 0.0));
        MvcResult secondResult = mockMvc.perform(post("/api/v1/emergency-alerts").cookie(loginCookieFor(familyOwner))
                        .contentType("application/json").content(secondBody))
                .andExpect(status().isCreated()).andReturn();
        Long furtherAlertId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/v1/emergency-alerts/nearby").cookie(loginCookieFor(nearCaretakerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(nearerAlertId))
                .andExpect(jsonPath("$.data[0].distanceKm").value(org.hamcrest.Matchers.closeTo(1.112, 0.01)))
                .andExpect(jsonPath("$.data[0].elderName").value("Robert"))
                .andExpect(jsonPath("$.data[1].id").value(furtherAlertId));

        // The far caretaker's own radius (10km) doesn't reach either alert (33.4km away) -
        // sees nothing at all.
        mockMvc.perform(get("/api/v1/emergency-alerts/nearby").cookie(loginCookieFor(farCaretakerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nearbyNoLongerIncludesAnAlertOnceItsBeenResolved() throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        User familyOwner = persistUser("ea-nearby-resolved-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("ea-nearby-resolved-caretaker@example.com", UserTypeEnum.CARETAKER);
        persistCaretaker(caretakerUser.getId(), 0.0, 0.0, 10.0, CaretakerVerificationStatusEnum.VERIFIED);
        markOnline(caretakerUser.getId());
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        Long alertId = triggerAlert(familyOwner, elder);

        mockMvc.perform(get("/api/v1/emergency-alerts/nearby").cookie(loginCookieFor(caretakerUser)))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/claim").cookie(loginCookieFor(caretakerUser)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/emergency-alerts/" + alertId + "/resolve").cookie(loginCookieFor(caretakerUser)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emergency-alerts/nearby").cookie(loginCookieFor(caretakerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nearbyReturns404ForACaretakerWithNoProfileYet() throws Exception {
        User caretakerUser = persistUser("ea-nearby-noprofile@example.com", UserTypeEnum.CARETAKER);

        mockMvc.perform(get("/api/v1/emergency-alerts/nearby").cookie(loginCookieFor(caretakerUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminGetAllListsEveryAlertAndFiltersByStatusAndElderProfileId() throws Exception {
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        User familyOwner = persistUser("ea-admin-list-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistAdminWithEmergencyDispatch("ea-admin-list-admin@example.com");
        User plainAdmin = persistUser("ea-admin-list-plain-admin@example.com", UserTypeEnum.ADMIN);
        ElderProfile elderA = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        ElderProfile elderB = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Grandma Rose").build());
        Long alertA = triggerAlert(familyOwner, elderA);
        String bodyB = objectMapper.writeValueAsString(Map.of("elderProfileId", elderB.getId(), "latitude", 0.0, "longitude", 0.0));
        mockMvc.perform(post("/api/v1/emergency-alerts").cookie(loginCookieFor(familyOwner)).contentType("application/json").content(bodyB))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/emergency-alerts").cookie(loginCookieFor(plainAdmin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/emergency-alerts").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        mockMvc.perform(get("/api/v1/emergency-alerts").cookie(loginCookieFor(admin)).param("elderProfileId", elderA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(alertA));

        mockMvc.perform(get("/api/v1/emergency-alerts").cookie(loginCookieFor(admin)).param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void adminGetByIdReturnsTheFullAlertIncludingOverdueAndResponseTime() throws Exception {
        // Stubbed directly, not via the triggerAlert() helper - that helper stubs its own
        // null address internally, which would clobber this test's own stub if called
        // afterward (Mockito stubs are last-write-wins across the whole test).
        User familyOwner = persistUser("ea-admin-getbyid-owner@example.com", UserTypeEnum.FAMILY_MEMBER);
        User admin = persistAdminWithEmergencyDispatch("ea-admin-getbyid-admin@example.com");
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyOwner.getId()).fullName("Robert").build());
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn("MG Road, Bengaluru");
        String body = objectMapper.writeValueAsString(Map.of("elderProfileId", elder.getId(), "latitude", 0.0, "longitude", 0.0));
        MvcResult result = mockMvc.perform(post("/api/v1/emergency-alerts").cookie(loginCookieFor(familyOwner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        Long alertId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/v1/emergency-alerts/" + alertId).cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elderName").value("Robert"))
                .andExpect(jsonPath("$.data.address").value("MG Road, Bengaluru"))
                .andExpect(jsonPath("$.data.status").value("TRIGGERED"))
                .andExpect(jsonPath("$.data.isOverdue").value(false))
                .andExpect(jsonPath("$.data.responseTimeSeconds").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void adminGetByIdReturns404ForAnUnknownAlert() throws Exception {
        User admin = persistAdminWithEmergencyDispatch("ea-admin-getbyid-404-admin@example.com");

        mockMvc.perform(get("/api/v1/emergency-alerts/999999999").cookie(loginCookieFor(admin)))
                .andExpect(status().isNotFound());
    }

    // The actual /topic/emergency-alerts broadcast on claim/resolve/dispatchOverride
    // (Prompt 87) is covered at the unit level (EmergencyAlertServiceImplTest) - verifying
    // it here would mean @MockitoBean-replacing the real SimpMessagingTemplate this whole
    // class otherwise exercises for real (notifyCaretakers/notifyFamily's own pushes),
    // which risks masking an unrelated regression in THAT real wiring for a single
    // assertion this class doesn't otherwise need.
}
