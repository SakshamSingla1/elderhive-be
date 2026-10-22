package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.EmergencyAlertRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
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
    private ProximityMatchingService proximityMatchingService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;
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
}
