package com.elderhive.services;

import com.elderhive.dtos.Emergency.EmergencyAlertDispatchOverrideRequest;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;

public interface EmergencyAlertService {

    /**
     * The SOS entry point (Prompt 83): creates the EmergencyAlert, best-effort reverse-
     * geocodes its coordinates, runs Prompt 82's proximity query and notifies every
     * matched in-radius VERIFIED caretaker (EMERGENCY_ALERT_CARETAKER, every channel,
     * ignoring NotificationPreference), notifies every family member linked to the elder
     * profile - the profile's own original owner AND every FamilyElderLink co-manager, not
     * just whichever one happens to be caller (EMERGENCY_ALERT_FAMILY) - and writes an
     * AuditLog entry for the trigger itself plus one for every notification sent.
     */
    EmergencyAlertResponse trigger(User caller, EmergencyAlertTriggerRequest request) throws GenericException;

    /**
     * First claim wins (Prompt 84): sets respondingCaretakerId to the caller's own
     * CaretakerProfile and moves TRIGGERED -> ACKNOWLEDGED. Throws
     * EMERGENCY_ALERT_ALREADY_CLAIMED if the alert has already moved past TRIGGERED -
     * every claim attempt after the first one, regardless of who makes it.
     */
    EmergencyAlertResponse claim(Long id, User caller) throws GenericException;

    /**
     * Moves ACKNOWLEDGED -> RESOLVED and computes responseTimeSeconds (createdAt to now).
     * Only the claiming caretaker (respondingCaretakerId) or an admin with
     * EMERGENCY_DISPATCH may call this; an alert nobody has claimed yet (still TRIGGERED)
     * must go through dispatchOverride's forceResolve instead.
     */
    EmergencyAlertResponse resolve(Long id, User caller) throws GenericException;

    /**
     * EMERGENCY_DISPATCH-gated (enforced at the controller via AdminPermissionGuard):
     * either reassigns respondingCaretakerId (bumping TRIGGERED -> ACKNOWLEDGED if nobody
     * had claimed it yet) or force-resolves an alert regardless of whether anyone claimed
     * it - exactly one of EmergencyAlertDispatchOverrideRequest's two fields must be set.
     */
    EmergencyAlertResponse dispatchOverride(Long id, User caller, EmergencyAlertDispatchOverrideRequest request) throws GenericException;
}
