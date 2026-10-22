package com.elderhive.services;

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
}
