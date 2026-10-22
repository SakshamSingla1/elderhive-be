package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.Emergency.EmergencyAlertDispatchOverrideRequest;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.EmergencyAlertRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.seed.MetaConfigSeeder;
import com.elderhive.security.AdminPermissionGuard;
import com.elderhive.services.AdminConfigService;
import com.elderhive.services.AuditLogService;
import com.elderhive.services.NotificationService;
import com.elderhive.services.ProximityMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyAlertServiceImplTest {

    @Mock
    private EmergencyAlertRepository emergencyAlertRepository;
    @Mock
    private ElderProfileRepository elderProfileRepository;
    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;
    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProximityMatchingService proximityMatchingService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AdminConfigService adminConfigService;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;
    @Mock
    private NominatimGeocodingClient nominatimGeocodingClient;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmergencyAlertServiceImpl service;

    private static final Long ELDER_PROFILE_ID = 1L;
    private static final Long FAMILY_OWNER_ID = 10L;

    private User caller(long id, UserTypeEnum type) {
        return User.builder().id(id).userType(type).build();
    }

    private ElderProfile elderProfile() {
        return ElderProfile.builder().id(ELDER_PROFILE_ID).familyUserId(FAMILY_OWNER_ID).fullName("Robert").build();
    }

    private EmergencyAlertTriggerRequest request() {
        return EmergencyAlertTriggerRequest.builder().elderProfileId(ELDER_PROFILE_ID).latitude(12.9716).longitude(77.5946).build();
    }

    private CaretakerProximityMatch match(long caretakerProfileId, long caretakerUserId, double distanceKm) {
        CaretakerProfile profile = CaretakerProfile.builder().id(caretakerProfileId).userId(caretakerUserId).build();
        return CaretakerProximityMatch.builder().caretakerProfile(profile).distanceKm(distanceKm).build();
    }

    private void stubSave() {
        when(emergencyAlertRepository.save(any(EmergencyAlert.class))).thenAnswer(inv -> {
            EmergencyAlert alert = inv.getArgument(0);
            alert.setId(500L);
            return alert;
        });
    }

    private EmergencyAlert existingAlert(EmergencyAlertStatusEnum status, Long respondingCaretakerId, LocalDateTime createdAt) {
        // createdAt lives on Auditable (a plain @Data mapped superclass, not @SuperBuilder) -
        // EmergencyAlert's own @Builder doesn't expose inherited fields, so it's set via the
        // ordinary setter afterward.
        EmergencyAlert alert = EmergencyAlert.builder().id(500L).elderProfileId(ELDER_PROFILE_ID).triggeredByUserId(FAMILY_OWNER_ID)
                .latitude(12.9716).longitude(77.5946).status(status).respondingCaretakerId(respondingCaretakerId).build();
        alert.setCreatedAt(createdAt);
        return alert;
    }

    private void stubEscalationTimeoutMinutes(int minutes) throws GenericException {
        when(adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT))
                .thenReturn(MetaConfigResponse.builder().context("EMERGENCY_SETTINGS")
                        .data("{\"searchRadiusKm\": 10, \"escalationTimeoutMinutes\": " + minutes + "}").build());
    }

    @Test
    void throwsWhenTheElderProfileDoesNotExist() {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trigger(caller(FAMILY_OWNER_ID, UserTypeEnum.FAMILY_MEMBER), request()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void rejectsACallerWithNoRelationshipToTheElderProfile() {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(ELDER_PROFILE_ID, 999L)).thenReturn(false);

        assertThatThrownBy(() -> service.trigger(caller(999L, UserTypeEnum.FAMILY_MEMBER), request()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void notifiesEveryMatchedCaretakerViaSendUrgentNotSend() throws GenericException {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn("MG Road, Bengaluru");
        stubSave();
        when(proximityMatchingService.findMatchingCaretakers(12.9716, 77.5946))
                .thenReturn(List.of(match(1L, 101L, 1.1), match(2L, 102L, 4.4)));
        when(familyElderLinkRepository.findByElderProfileId(ELDER_PROFILE_ID)).thenReturn(List.of());

        service.trigger(caller(FAMILY_OWNER_ID, UserTypeEnum.FAMILY_MEMBER), request());

        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_CARETAKER"), variablesCaptor.capture(), eq(101L));
        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_CARETAKER"), any(), eq(102L));
        verify(notificationService, never()).send(anyString(), any(), any());
        assertThat(variablesCaptor.getValue()).containsEntry("elderName", "Robert").containsEntry("alertAddress", "MG Road, Bengaluru");
    }

    @Test
    void notifiesTheOriginalOwnerAndEveryCoManagingFamilyMemberNotJustOne() throws GenericException {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        stubSave();
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(ELDER_PROFILE_ID, 20L)).thenReturn(true);
        when(proximityMatchingService.findMatchingCaretakers(anyDouble(), anyDouble())).thenReturn(List.of());
        when(familyElderLinkRepository.findByElderProfileId(ELDER_PROFILE_ID)).thenReturn(List.of(
                FamilyElderLink.builder().elderProfileId(ELDER_PROFILE_ID).familyUserId(20L).build(),
                FamilyElderLink.builder().elderProfileId(ELDER_PROFILE_ID).familyUserId(21L).build()));

        // Co-managing, not the original owner - FAMILY_OWNER_ID (10) is the one with access here.
        service.trigger(caller(20L, UserTypeEnum.FAMILY_MEMBER), request());

        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), eq(FAMILY_OWNER_ID));
        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), eq(20L));
        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), eq(21L));
        verify(notificationService, times(3)).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), any());
    }

    @Test
    void falsyAddressFallsBackToAPlaceholderRatherThanAThrownPlaceholderError() throws GenericException {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        stubSave();
        when(proximityMatchingService.findMatchingCaretakers(anyDouble(), anyDouble())).thenReturn(List.of());
        when(familyElderLinkRepository.findByElderProfileId(ELDER_PROFILE_ID)).thenReturn(List.of());

        EmergencyAlertResponse response = service.trigger(caller(FAMILY_OWNER_ID, UserTypeEnum.FAMILY_MEMBER), request());

        assertThat(response.getAddress()).isNull();
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), variablesCaptor.capture(), eq(FAMILY_OWNER_ID));
        assertThat(variablesCaptor.getValue().get("alertAddress")).isEqualTo("an unknown location");
    }

    @Test
    void oneFailedNotificationDoesNotPreventTheRestOfTheFanOut() throws GenericException {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);
        stubSave();
        when(proximityMatchingService.findMatchingCaretakers(anyDouble(), anyDouble())).thenReturn(List.of());
        when(familyElderLinkRepository.findByElderProfileId(ELDER_PROFILE_ID)).thenReturn(List.of(
                FamilyElderLink.builder().elderProfileId(ELDER_PROFILE_ID).familyUserId(20L).build()));
        // The original owner's own send fails - the co-manager must still be notified.
        org.mockito.Mockito.doThrow(new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "gone"))
                .when(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), eq(FAMILY_OWNER_ID));

        service.trigger(caller(FAMILY_OWNER_ID, UserTypeEnum.FAMILY_MEMBER), request());

        verify(notificationService).sendUrgent(eq("EMERGENCY_ALERT_FAMILY"), any(), eq(20L));
    }

    @Test
    void logsAnAuditTrailForTheTriggerAndEveryNotificationSent() throws GenericException {
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        when(nominatimGeocodingClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn("MG Road");
        stubSave();
        when(proximityMatchingService.findMatchingCaretakers(anyDouble(), anyDouble())).thenReturn(List.of(match(1L, 101L, 2.0)));
        when(familyElderLinkRepository.findByElderProfileId(ELDER_PROFILE_ID)).thenReturn(List.of());

        service.trigger(caller(FAMILY_OWNER_ID, UserTypeEnum.FAMILY_MEMBER), request());

        // 1 CREATE (the alert itself) + 2 NOTIFY (the one matched caretaker, and the elder
        // profile's own original owner - who happens to be the caller here too; trigger()
        // deliberately doesn't special-case "don't notify the person who just triggered it".
        verify(auditLogService).record(eq(EntityTypeEnum.EMERGENCY_ALERT), eq("500"), eq(ActionType.CREATE), eq(FAMILY_OWNER_ID), anyString());
        verify(auditLogService, times(2)).record(eq(EntityTypeEnum.EMERGENCY_ALERT), eq("500"), eq(ActionType.NOTIFY), eq(FAMILY_OWNER_ID), anyString());
        verify(auditLogService, times(3)).record(eq(EntityTypeEnum.EMERGENCY_ALERT), eq("500"), any(), eq(FAMILY_OWNER_ID), anyString());
    }

    // ==== claim ====

    @Test
    void claimMovesTriggeredToAcknowledgedAndSetsTheRespondingCaretaker() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);
        stubSave();

        EmergencyAlertResponse response = service.claim(500L, caller(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.ACKNOWLEDGED);
        assertThat(response.getRespondingCaretakerId()).isEqualTo(30L);
    }

    @Test
    void claimRejectsAnAlertThatHasAlreadyMovedPastTriggered() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now())));

        assertThatThrownBy(() -> service.claim(500L, caller(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.EMERGENCY_ALERT_ALREADY_CLAIMED);
    }

    @Test
    void claimThrowsWhenTheCallerHasNoCaretakerProfile() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(500L, caller(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void claimThrowsWhenTheAlertDoesNotExist() {
        when(emergencyAlertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(999L, caller(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.EMERGENCY_ALERT_NOT_FOUND);
    }

    // ==== resolve ====

    @Test
    void resolveComputesResponseTimeSecondsAndMovesToResolvedForTheClaimingCaretaker() throws GenericException {
        LocalDateTime createdAt = LocalDateTime.now().minusSeconds(90);
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, createdAt)));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubSave();

        EmergencyAlertResponse response = service.resolve(500L, caller(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.RESOLVED);
        assertThat(response.getResponseTimeSeconds()).isGreaterThanOrEqualTo(90);
        assertThat(response.isOverdue()).isFalse();
    }

    @Test
    void resolveAllowsAnAdminWithEmergencyDispatch() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now())));
        when(adminPermissionGuard.has("EMERGENCY_DISPATCH")).thenReturn(true);
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubSave();

        EmergencyAlertResponse response = service.resolve(500L, caller(900L, UserTypeEnum.ADMIN));

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.RESOLVED);
    }

    @Test
    void resolveRejectsACaretakerWhoDidNotClaimIt() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now())));
        when(caretakerProfileRepository.findByUserId(999L)).thenReturn(Optional.of(CaretakerProfile.builder().id(31L).userId(999L).build()));

        assertThatThrownBy(() -> service.resolve(500L, caller(999L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void resolveRejectsAnAdminWithoutEmergencyDispatch() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now())));
        when(adminPermissionGuard.has("EMERGENCY_DISPATCH")).thenReturn(false);

        assertThatThrownBy(() -> service.resolve(500L, caller(900L, UserTypeEnum.ADMIN)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void resolveRejectsAnAlertNobodyHasClaimedYet() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));
        when(adminPermissionGuard.has("EMERGENCY_DISPATCH")).thenReturn(true);

        assertThatThrownBy(() -> service.resolve(500L, caller(900L, UserTypeEnum.ADMIN)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_EMERGENCY_ALERT_STATUS_TRANSITION);
    }

    // ==== dispatchOverride ====

    @Test
    void dispatchOverrideReassignsAndBumpsTriggeredToAcknowledged() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));
        when(caretakerProfileRepository.findById(31L)).thenReturn(Optional.of(CaretakerProfile.builder().id(31L).userId(201L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);
        stubSave();

        EmergencyAlertResponse response = service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().respondingCaretakerId(31L).build());

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.ACKNOWLEDGED);
        assertThat(response.getRespondingCaretakerId()).isEqualTo(31L);
    }

    @Test
    void dispatchOverrideReassigningAnAlreadyAcknowledgedAlertDoesNotChangeItsStatus() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now())));
        when(caretakerProfileRepository.findById(31L)).thenReturn(Optional.of(CaretakerProfile.builder().id(31L).userId(201L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);
        stubSave();

        EmergencyAlertResponse response = service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().respondingCaretakerId(31L).build());

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.ACKNOWLEDGED);
        assertThat(response.getRespondingCaretakerId()).isEqualTo(31L);
    }

    @Test
    void dispatchOverrideForceResolvesRegardlessOfWhetherAnyoneClaimedIt() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now().minusMinutes(5))));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubSave();

        EmergencyAlertResponse response = service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().forceResolve(true).build());

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.RESOLVED);
        assertThat(response.getResponseTimeSeconds()).isGreaterThanOrEqualTo(300);
    }

    @Test
    void dispatchOverrideRejectsNeitherFieldSet() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));

        assertThatThrownBy(() -> service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);
    }

    @Test
    void dispatchOverrideRejectsBothFieldsSet() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now())));

        assertThatThrownBy(() -> service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().respondingCaretakerId(31L).forceResolve(true).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);
    }

    @Test
    void dispatchOverrideRejectsAnAlreadyResolvedAlert() {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(existingAlert(EmergencyAlertStatusEnum.RESOLVED, 30L, LocalDateTime.now())));

        assertThatThrownBy(() -> service.dispatchOverride(500L, caller(900L, UserTypeEnum.ADMIN),
                EmergencyAlertDispatchOverrideRequest.builder().forceResolve(true).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_EMERGENCY_ALERT_STATUS_TRANSITION);
    }

    // ==== isOverdue ====

    @Test
    void isOverdueIsTrueWhenPastTheEscalationTimeoutAndNotResolved() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(
                existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now().minusMinutes(20))));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);
        stubSave();

        EmergencyAlertResponse response = service.claim(500L, caller(200L, UserTypeEnum.CARETAKER));

        assertThat(response.isOverdue()).isTrue();
    }

    @Test
    void isOverdueIsFalseWhenWithinTheEscalationTimeout() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(
                existingAlert(EmergencyAlertStatusEnum.TRIGGERED, null, LocalDateTime.now().minusMinutes(5))));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);
        stubSave();

        EmergencyAlertResponse response = service.claim(500L, caller(200L, UserTypeEnum.CARETAKER));

        assertThat(response.isOverdue()).isFalse();
    }

    @Test
    void isOverdueIsFalseWhenResolvedRegardlessOfElapsedTime() throws GenericException {
        when(emergencyAlertRepository.findById(500L)).thenReturn(Optional.of(
                existingAlert(EmergencyAlertStatusEnum.ACKNOWLEDGED, 30L, LocalDateTime.now().minusHours(5))));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubSave();

        EmergencyAlertResponse response = service.resolve(500L, caller(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(EmergencyAlertStatusEnum.RESOLVED);
        assertThat(response.isOverdue()).isFalse();
    }

    // ==== findNearbyActiveAlerts ====

    private EmergencyAlert activeAlert(long id, double latitude, double longitude, EmergencyAlertStatusEnum status) {
        EmergencyAlert alert = EmergencyAlert.builder().id(id).elderProfileId(ELDER_PROFILE_ID).triggeredByUserId(FAMILY_OWNER_ID)
                .latitude(latitude).longitude(longitude).status(status).build();
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }

    @Test
    void findNearbyActiveAlertsReturnsOnlyAlertsWhereTheCallerIsACurrentMatchOrderedByDistance() throws GenericException {
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(elderProfileRepository.findById(ELDER_PROFILE_ID)).thenReturn(Optional.of(elderProfile()));
        stubEscalationTimeoutMinutes(15);

        EmergencyAlert nearAlert = activeAlert(501L, 0.01, 0.0, EmergencyAlertStatusEnum.TRIGGERED);
        EmergencyAlert farAlert = activeAlert(502L, 0.04, 0.0, EmergencyAlertStatusEnum.ACKNOWLEDGED);
        // Some OTHER caretaker's own match, never this caller's - must not appear at all.
        EmergencyAlert notMyMatchAlert = activeAlert(503L, 0.1, 0.0, EmergencyAlertStatusEnum.TRIGGERED);

        // Deliberately returned out of distance order - the service must sort, not just
        // pass the repository's own ordering through.
        when(emergencyAlertRepository.findByStatusIn(List.of(EmergencyAlertStatusEnum.TRIGGERED, EmergencyAlertStatusEnum.ACKNOWLEDGED)))
                .thenReturn(List.of(farAlert, nearAlert, notMyMatchAlert));
        when(proximityMatchingService.findMatchingCaretakers(0.01, 0.0)).thenReturn(List.of(match(30L, 200L, 1.1)));
        when(proximityMatchingService.findMatchingCaretakers(0.04, 0.0)).thenReturn(List.of(match(30L, 200L, 4.4)));
        when(proximityMatchingService.findMatchingCaretakers(0.1, 0.0)).thenReturn(List.of(match(31L, 201L, 2.0)));

        List<EmergencyAlertResponse> result = service.findNearbyActiveAlerts(caller(200L, UserTypeEnum.CARETAKER));

        assertThat(result).extracting(EmergencyAlertResponse::getId).containsExactly(501L, 502L);
        assertThat(result.get(0).getDistanceKm()).isEqualTo(1.1);
        assertThat(result.get(1).getDistanceKm()).isEqualTo(4.4);
    }

    @Test
    void findNearbyActiveAlertsThrowsWhenTheCallerHasNoCaretakerProfile() {
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findNearbyActiveAlerts(caller(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void findNearbyActiveAlertsReturnsEmptyWhenNoActiveAlertCurrentlyMatchesTheCaller() throws GenericException {
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        EmergencyAlert alert = activeAlert(501L, 0.1, 0.0, EmergencyAlertStatusEnum.TRIGGERED);
        when(emergencyAlertRepository.findByStatusIn(any())).thenReturn(List.of(alert));
        when(proximityMatchingService.findMatchingCaretakers(0.1, 0.0)).thenReturn(List.of());

        assertThat(service.findNearbyActiveAlerts(caller(200L, UserTypeEnum.CARETAKER))).isEmpty();
    }

    @Test
    void findNearbyActiveAlertsOnlyQueriesTriggeredAndAcknowledgedStatuses() throws GenericException {
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(CaretakerProfile.builder().id(30L).userId(200L).build()));
        when(emergencyAlertRepository.findByStatusIn(any())).thenReturn(List.of());

        service.findNearbyActiveAlerts(caller(200L, UserTypeEnum.CARETAKER));

        verify(emergencyAlertRepository).findByStatusIn(List.of(EmergencyAlertStatusEnum.TRIGGERED, EmergencyAlertStatusEnum.ACKNOWLEDGED));
    }
}
