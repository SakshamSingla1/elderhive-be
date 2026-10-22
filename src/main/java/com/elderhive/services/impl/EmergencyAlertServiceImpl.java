package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
import com.elderhive.entities.User;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.EmergencyAlertRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.services.AuditLogService;
import com.elderhive.services.EmergencyAlertService;
import com.elderhive.services.NotificationService;
import com.elderhive.services.ProximityMatchingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "The single most consequential flow in the app" (Prompt 83's own words) - every
 * notification send below is deliberately wrapped so ONE bad recipient (a stale user
 * reference, a transient failure) can never prevent the rest of the fan-out from running,
 * and can never roll back the EmergencyAlert row itself. GenericException is a checked
 * exception (see its own class), so Spring's default @Transactional rollback rule - which
 * only auto-rolls-back on unchecked exceptions - never fires for it anyway; the per-
 * recipient try/catch below exists to keep the LOOP going, not to prevent a rollback that
 * was never going to happen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyAlertServiceImpl implements EmergencyAlertService {

    private static final String UNKNOWN_LOCATION = "an unknown location";

    private final EmergencyAlertRepository emergencyAlertRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final ProximityMatchingService proximityMatchingService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final NominatimGeocodingClient nominatimGeocodingClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EmergencyAlertResponse trigger(User caller, EmergencyAlertTriggerRequest request) throws GenericException {
        ElderProfile elderProfile = elderProfileRepository.findById(request.getElderProfileId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));
        assertCanTrigger(elderProfile, caller);

        String address = nominatimGeocodingClient.reverseGeocode(request.getLatitude(), request.getLongitude());

        EmergencyAlert alert = emergencyAlertRepository.save(EmergencyAlert.builder()
                .elderProfileId(elderProfile.getId())
                .triggeredByUserId(caller.getId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .address(address)
                .status(EmergencyAlertStatusEnum.TRIGGERED)
                .build());

        auditLogService.record(EntityTypeEnum.EMERGENCY_ALERT, alert.getId().toString(), ActionType.CREATE, caller.getId(),
                toJson(Map.of(
                        "elderProfileId", elderProfile.getId(),
                        "latitude", request.getLatitude(),
                        "longitude", request.getLongitude(),
                        "address", address != null ? address : "unresolved"
                )));

        Map<String, String> variables = Map.of(
                "elderName", elderProfile.getFullName(),
                "alertAddress", address != null ? address : UNKNOWN_LOCATION
        );
        notifyCaretakers(alert, variables);
        notifyFamily(alert, elderProfile, variables);

        return toResponse(alert, elderProfile);
    }

    private void assertCanTrigger(ElderProfile elderProfile, User caller) throws GenericException {
        boolean isElderSelf = Objects.equals(elderProfile.getElderUserId(), caller.getId());
        boolean isFamilyOwner = Objects.equals(elderProfile.getFamilyUserId(), caller.getId());
        boolean isCoManaging = familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(elderProfile.getId(), caller.getId());
        if (!isElderSelf && !isFamilyOwner && !isCoManaging) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to trigger an emergency alert for this elder profile");
        }
    }

    // Every VERIFIED, in-radius (Prompt 82) caretaker - all channels, ignoring
    // NotificationPreference entirely (sendUrgent, not send): an emergency overrides
    // channel preferences by design.
    private void notifyCaretakers(EmergencyAlert alert, Map<String, String> variables) throws GenericException {
        List<CaretakerProximityMatch> matches = proximityMatchingService.findMatchingCaretakers(alert.getLatitude(), alert.getLongitude());
        for (CaretakerProximityMatch match : matches) {
            Long caretakerUserId = match.getCaretakerProfile().getUserId();
            try {
                notificationService.sendUrgent("EMERGENCY_ALERT_CARETAKER", variables, caretakerUserId);
                auditLogService.record(EntityTypeEnum.EMERGENCY_ALERT, alert.getId().toString(), ActionType.NOTIFY, alert.getTriggeredByUserId(),
                        toJson(Map.of(
                                "recipientType", "CARETAKER",
                                "recipientUserId", caretakerUserId,
                                "caretakerProfileId", match.getCaretakerProfile().getId(),
                                "distanceKm", match.getDistanceKm()
                        )));
            } catch (GenericException e) {
                log.error("Failed to notify caretaker userId={} for emergency alert {}: {}", caretakerUserId, alert.getId(), e.getErrorMessage());
            }
        }
    }

    // The elder profile's own original owner (ElderProfile.familyUserId) PLUS every
    // FamilyElderLink co-manager - not just whichever one happened to trigger the alert.
    // This is the actual ElderSphere bug fix Prompt 83 calls out: ElderSphere only ever
    // notified the original owner.
    private void notifyFamily(EmergencyAlert alert, ElderProfile elderProfile, Map<String, String> variables) {
        Set<Long> familyUserIds = new LinkedHashSet<>();
        if (elderProfile.getFamilyUserId() != null) {
            familyUserIds.add(elderProfile.getFamilyUserId());
        }
        familyElderLinkRepository.findByElderProfileId(elderProfile.getId())
                .forEach(link -> familyUserIds.add(link.getFamilyUserId()));

        for (Long familyUserId : familyUserIds) {
            try {
                notificationService.sendUrgent("EMERGENCY_ALERT_FAMILY", variables, familyUserId);
                auditLogService.record(EntityTypeEnum.EMERGENCY_ALERT, alert.getId().toString(), ActionType.NOTIFY, alert.getTriggeredByUserId(),
                        toJson(Map.of("recipientType", "FAMILY", "recipientUserId", familyUserId)));
            } catch (GenericException e) {
                log.error("Failed to notify family member userId={} for emergency alert {}: {}", familyUserId, alert.getId(), e.getErrorMessage());
            }
        }
    }

    private EmergencyAlertResponse toResponse(EmergencyAlert alert, ElderProfile elderProfile) {
        return EmergencyAlertResponse.builder()
                .id(alert.getId())
                .elderProfileId(alert.getElderProfileId())
                .elderName(elderProfile != null ? elderProfile.getFullName() : null)
                .triggeredByUserId(alert.getTriggeredByUserId())
                .latitude(alert.getLatitude())
                .longitude(alert.getLongitude())
                .address(alert.getAddress())
                .status(alert.getStatus())
                .respondingCaretakerId(alert.getRespondingCaretakerId())
                .responseTimeSeconds(alert.getResponseTimeSeconds())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    private String toJson(Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data);
        }
    }
}
