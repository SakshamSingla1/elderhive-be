package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.Emergency.EmergencyAlertDispatchOverrideRequest;
import com.elderhive.dtos.Emergency.EmergencyAlertOpenCounts;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.EmergencyAlert;
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
import com.elderhive.services.EmergencyAlertService;
import com.elderhive.services.NotificationService;
import com.elderhive.services.ProximityMatchingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final List<EmergencyAlertStatusEnum> OPEN_STATUSES =
            List.of(EmergencyAlertStatusEnum.TRIGGERED, EmergencyAlertStatusEnum.ACKNOWLEDGED);

    private final EmergencyAlertRepository emergencyAlertRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final ProximityMatchingService proximityMatchingService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final AdminConfigService adminConfigService;
    private final AdminPermissionGuard adminPermissionGuard;
    private final NominatimGeocodingClient nominatimGeocodingClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // Broadcast-to-everyone-watching topic (Prompt 87) - deliberately NOT a per-user
    // Notification (no inbox row, no NotificationTemplate, no delivery log): claim/
    // resolve/dispatchOverride don't need to tell any ONE specific person something new
    // happened to THEM, they need every caretaker/admin screen that's already showing
    // this alert to resync. trigger() doesn't publish here - its own real fan-out
    // (notifyCaretakers/notifyFamily) already covers "something new is happening".
    private static final String EMERGENCY_ALERTS_TOPIC = "/topic/emergency-alerts";

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

    @Override
    @Transactional
    public EmergencyAlertResponse claim(Long id, User caller) throws GenericException {
        EmergencyAlert alert = findOrThrow(id);

        // First claim wins: checked BEFORE resolving the caller's own CaretakerProfile -
        // a property of the alert itself, so every claim attempt after the one that
        // actually lands here is rejected on that basis alone, regardless of who's asking
        // (including a second attempt by the same caretaker who claimed it first).
        if (alert.getStatus() != EmergencyAlertStatusEnum.TRIGGERED) {
            throw new GenericException(ExceptionCodeEnum.EMERGENCY_ALERT_ALREADY_CLAIMED,
                    "This alert has already moved past TRIGGERED (current status: " + alert.getStatus() + ")");
        }
        CaretakerProfile caretakerProfile = caretakerProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
        alert.setRespondingCaretakerId(caretakerProfile.getId());
        alert.setStatus(EmergencyAlertStatusEnum.ACKNOWLEDGED);
        EmergencyAlert saved = emergencyAlertRepository.save(alert);

        EmergencyAlertResponse response = toResponse(saved, elderProfileRepository.findById(saved.getElderProfileId()).orElse(null));
        broadcastStatusChanged(response);
        return response;
    }

    @Override
    @Transactional
    public EmergencyAlertResponse resolve(Long id, User caller) throws GenericException {
        EmergencyAlert alert = findOrThrow(id);
        assertCanResolve(alert, caller);
        if (alert.getStatus() != EmergencyAlertStatusEnum.ACKNOWLEDGED) {
            throw new GenericException(ExceptionCodeEnum.INVALID_EMERGENCY_ALERT_STATUS_TRANSITION,
                    "Cannot resolve this alert - it must be ACKNOWLEDGED (claimed) first, but it is " + alert.getStatus()
                            + ". Use the admin dispatch-override endpoint to force-resolve an alert nobody has claimed.");
        }
        markResolved(alert);
        EmergencyAlert saved = emergencyAlertRepository.save(alert);

        EmergencyAlertResponse response = toResponse(saved, elderProfileRepository.findById(saved.getElderProfileId()).orElse(null));
        broadcastStatusChanged(response);
        return response;
    }

    @Override
    @Transactional
    public EmergencyAlertResponse dispatchOverride(Long id, User caller, EmergencyAlertDispatchOverrideRequest request) throws GenericException {
        EmergencyAlert alert = findOrThrow(id);
        boolean reassigning = request.getRespondingCaretakerId() != null;
        if (reassigning == request.isForceResolve()) {
            throw new GenericException(ExceptionCodeEnum.VALIDATION_FAILED,
                    "Provide exactly one of respondingCaretakerId (reassign) or forceResolve=true (force-resolve)");
        }
        if (alert.getStatus() == EmergencyAlertStatusEnum.RESOLVED) {
            throw new GenericException(ExceptionCodeEnum.INVALID_EMERGENCY_ALERT_STATUS_TRANSITION, "This alert is already RESOLVED");
        }

        if (reassigning) {
            CaretakerProfile reassignedCaretaker = caretakerProfileRepository.findById(request.getRespondingCaretakerId())
                    .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
            alert.setRespondingCaretakerId(reassignedCaretaker.getId());
            if (alert.getStatus() == EmergencyAlertStatusEnum.TRIGGERED) {
                alert.setStatus(EmergencyAlertStatusEnum.ACKNOWLEDGED);
            }
        } else {
            markResolved(alert);
        }
        EmergencyAlert saved = emergencyAlertRepository.save(alert);

        EmergencyAlertResponse response = toResponse(saved, elderProfileRepository.findById(saved.getElderProfileId()).orElse(null));
        broadcastStatusChanged(response);
        return response;
    }

    @Override
    public Page<EmergencyAlertResponse> getAll(Long elderProfileId, EmergencyAlertStatusEnum status, Pageable pageable) {
        return emergencyAlertRepository.findByCriteria(elderProfileId, status, pageable)
                .map(this::toResponseUnchecked);
    }

    @Override
    public EmergencyAlertResponse getById(Long id) throws GenericException {
        EmergencyAlert alert = findOrThrow(id);
        return toResponse(alert, elderProfileRepository.findById(alert.getElderProfileId()).orElse(null));
    }

    @Override
    public EmergencyAlertOpenCounts countOpenAlerts() throws GenericException {
        List<EmergencyAlert> openAlerts = emergencyAlertRepository.findByStatusIn(OPEN_STATUSES);
        long overdue = 0;
        for (EmergencyAlert alert : openAlerts) {
            if (computeIsOverdue(alert)) {
                overdue++;
            }
        }
        return EmergencyAlertOpenCounts.builder().open(openAlerts.size()).overdue(overdue).build();
    }

    // Page#map's Function<T, R> signature has no checked-exception escape hatch, unlike
    // every other caller here - toResponse only ever actually throws when
    // resolveEscalationTimeoutMinutes's EMERGENCY_SETTINGS read fails to parse, which would
    // be a system misconfiguration, not a per-row problem, so this surfaces it as the
    // generic 500 UNEXPECTED_ERROR rather than silently dropping rows or wrapping every
    // row's own GenericException in something misleading.
    private EmergencyAlertResponse toResponseUnchecked(EmergencyAlert alert) {
        try {
            return toResponse(alert, elderProfileRepository.findById(alert.getElderProfileId()).orElse(null));
        } catch (GenericException e) {
            throw new RuntimeException(e.getErrorMessage(), e);
        }
    }

    private void broadcastStatusChanged(EmergencyAlertResponse response) {
        messagingTemplate.convertAndSend(EMERGENCY_ALERTS_TOPIC, response);
    }

    @Override
    public List<EmergencyAlertResponse> findNearbyActiveAlerts(User caller) throws GenericException {
        CaretakerProfile caretakerProfile = caretakerProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));

        List<EmergencyAlert> activeAlerts = emergencyAlertRepository.findByStatusIn(OPEN_STATUSES);

        List<EmergencyAlertResponse> nearby = new ArrayList<>();
        for (EmergencyAlert alert : activeAlerts) {
            // Re-runs Prompt 82's own query from THIS alert's coordinates and keeps only
            // the match that's this caller's own CaretakerProfile (if present) - live
            // against the caretaker's CURRENT location/serviceRadiusKm, not a snapshot of
            // who was matched back when the alert first triggered.
            Optional<CaretakerProximityMatch> ownMatch = proximityMatchingService
                    .findMatchingCaretakers(alert.getLatitude(), alert.getLongitude()).stream()
                    .filter(match -> Objects.equals(match.getCaretakerProfile().getId(), caretakerProfile.getId()))
                    .findFirst();
            if (ownMatch.isPresent()) {
                ElderProfile elderProfile = elderProfileRepository.findById(alert.getElderProfileId()).orElse(null);
                nearby.add(toResponse(alert, elderProfile, ownMatch.get().getDistanceKm()));
            }
        }
        nearby.sort(Comparator.comparing(EmergencyAlertResponse::getDistanceKm, Comparator.nullsLast(Comparator.naturalOrder())));
        return nearby;
    }

    private EmergencyAlert findOrThrow(Long id) throws GenericException {
        return emergencyAlertRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.EMERGENCY_ALERT_NOT_FOUND, "Emergency alert not found"));
    }

    /** The claiming caretaker (respondingCaretakerId) or an admin/super-admin with EMERGENCY_DISPATCH. */
    private void assertCanResolve(EmergencyAlert alert, User caller) throws GenericException {
        if (caller.getUserType() == UserTypeEnum.CARETAKER) {
            CaretakerProfile callerProfile = caretakerProfileRepository.findByUserId(caller.getId()).orElse(null);
            if (callerProfile != null && Objects.equals(callerProfile.getId(), alert.getRespondingCaretakerId())) {
                return;
            }
        } else if (adminPermissionGuard.has("EMERGENCY_DISPATCH")) {
            return;
        }
        throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "Only the claiming caretaker or an admin with EMERGENCY_DISPATCH may resolve this alert");
    }

    private void markResolved(EmergencyAlert alert) {
        alert.setStatus(EmergencyAlertStatusEnum.RESOLVED);
        if (alert.getCreatedAt() != null) {
            alert.setResponseTimeSeconds((int) Duration.between(alert.getCreatedAt(), LocalDateTime.now()).getSeconds());
        }
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

    private EmergencyAlertResponse toResponse(EmergencyAlert alert, ElderProfile elderProfile) throws GenericException {
        return toResponse(alert, elderProfile, null);
    }

    // distanceKm is only ever non-null from findNearbyActiveAlerts above - every other
    // caller passes null (see EmergencyAlertResponse.distanceKm's own javadoc).
    private EmergencyAlertResponse toResponse(EmergencyAlert alert, ElderProfile elderProfile, Double distanceKm) throws GenericException {
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
                .respondingCaretakerName(resolveCaretakerName(alert.getRespondingCaretakerId()))
                .responseTimeSeconds(alert.getResponseTimeSeconds())
                .isOverdue(computeIsOverdue(alert))
                .distanceKm(distanceKm)
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    private String resolveCaretakerName(Long respondingCaretakerId) {
        if (respondingCaretakerId == null) {
            return null;
        }
        return caretakerProfileRepository.findById(respondingCaretakerId)
                .map(CaretakerProfile::getUserId)
                .flatMap(userRepository::findById)
                .map(User::getFullName)
                .orElse(null);
    }

    // now - createdAt > EMERGENCY_SETTINGS.escalationTimeoutMinutes (Prompt 37) &&
    // status != RESOLVED - computed fresh on every read, no background job (Prompt 84's
    // own documented scope: that's a later phase's job once one exists).
    private boolean computeIsOverdue(EmergencyAlert alert) throws GenericException {
        if (alert.getStatus() == EmergencyAlertStatusEnum.RESOLVED || alert.getCreatedAt() == null) {
            return false;
        }
        return Duration.between(alert.getCreatedAt(), LocalDateTime.now()).toMinutes() > resolveEscalationTimeoutMinutes();
    }

    private int resolveEscalationTimeoutMinutes() throws GenericException {
        MetaConfigResponse config = adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT);
        JsonNode data;
        try {
            data = objectMapper.readTree(config.getData());
        } catch (JsonProcessingException e) {
            throw new GenericException(ExceptionCodeEnum.FORMAT_ERROR, "EMERGENCY_SETTINGS config is not valid JSON.");
        }
        JsonNode escalationTimeoutMinutes = data.get("escalationTimeoutMinutes");
        if (escalationTimeoutMinutes == null || !escalationTimeoutMinutes.isNumber()) {
            throw new GenericException(ExceptionCodeEnum.FORMAT_ERROR, "EMERGENCY_SETTINGS config is missing a numeric escalationTimeoutMinutes.");
        }
        return escalationTimeoutMinutes.asInt();
    }

    private String toJson(Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data);
        }
    }
}
