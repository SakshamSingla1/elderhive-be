package com.elderhive.services;

import com.elderhive.dtos.Emergency.EmergencyAlertDispatchOverrideRequest;
import com.elderhive.dtos.Emergency.EmergencyAlertOpenCounts;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.entities.User;
import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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

    /**
     * Prompt 86's caretaker queue: every TRIGGERED or ACKNOWLEDGED alert (system-wide)
     * that the caller's own CaretakerProfile currently matches via Prompt 82's proximity
     * query - re-run live, per active alert, against the caller's CURRENT location/radius
     * rather than a stale snapshot of who was matched at trigger time, so a caretaker who
     * moves (or updates their own serviceRadiusKm) sees an accurate list on every call.
     * Ordered nearest-first; each result's distanceKm is set (every other endpoint leaves
     * it null).
     */
    List<EmergencyAlertResponse> findNearbyActiveAlerts(User caller) throws GenericException;

    /** Prompt 87's admin oversight listing - every alert system-wide, optionally filtered
     *  by elderProfileId/status, EMERGENCY_DISPATCH-gated at the controller. */
    Page<EmergencyAlertResponse> getAll(Long elderProfileId, EmergencyAlertStatusEnum status, Pageable pageable);

    /** Prompt 87's admin detail view - a single alert by id, EMERGENCY_DISPATCH-gated at
     *  the controller. No non-admin caller needs this (everyone else reaches an alert
     *  through trigger/claim/resolve/nearby's own responses instead). */
    EmergencyAlertResponse getById(Long id) throws GenericException;

    /** Prompt 94's admin dashboard stat: every non-RESOLVED alert system-wide, and how
     *  many of those are currently overdue - reuses this service's own isOverdue rule
     *  (EMERGENCY_SETTINGS.escalationTimeoutMinutes) rather than a second implementation
     *  of it living in AdminDashboardService. */
    EmergencyAlertOpenCounts countOpenAlerts() throws GenericException;
}
