package com.elderhive.services;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface ProximityMatchingService {

    /**
     * Finds every VERIFIED caretaker within range of the given point (Prompt 83's trigger
     * flow calls this with an EmergencyAlert's own latitude/longitude), ordered nearest
     * first. "In range" means within the caretaker's own serviceRadiusKm, or - when they
     * haven't set one - MetaConfig's EMERGENCY_SETTINGS.searchRadiusKm default.
     */
    List<CaretakerProximityMatch> findMatchingCaretakers(double latitude, double longitude) throws GenericException;
}
