package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.seed.MetaConfigSeeder;
import com.elderhive.services.AdminConfigService;
import com.elderhive.services.ProximityMatchingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProximityMatchingServiceImpl implements ProximityMatchingService {

    private final CaretakerProfileRepository caretakerProfileRepository;
    private final AdminConfigService adminConfigService;
    private final ObjectMapper objectMapper;

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public List<CaretakerProximityMatch> findMatchingCaretakers(double latitude, double longitude) throws GenericException {
        double defaultRadiusKm = resolveDefaultSearchRadiusKm();

        // Already filtered to VERIFIED + in-radius and ordered nearest-first by the
        // repository's own native haversine query - the stream below only re-derives each
        // match's own distanceKm (for CaretakerProximityMatch/Prompt 83's audit log) and
        // applies the presence stub, neither of which changes that ordering.
        return caretakerProfileRepository.findVerifiedWithinRadius(latitude, longitude, defaultRadiusKm).stream()
                .filter(this::isOnline)
                .map(caretaker -> CaretakerProximityMatch.builder()
                        .caretakerProfile(caretaker)
                        .distanceKm(haversineKm(latitude, longitude, caretaker.getLatitude(), caretaker.getLongitude()))
                        .build())
                .toList();
    }

    private double resolveDefaultSearchRadiusKm() throws GenericException {
        MetaConfigResponse config = adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT);
        JsonNode data;
        try {
            data = objectMapper.readTree(config.getData());
        } catch (JsonProcessingException e) {
            throw new GenericException(ExceptionCodeEnum.FORMAT_ERROR, "EMERGENCY_SETTINGS config is not valid JSON.");
        }
        JsonNode searchRadiusKm = data.get("searchRadiusKm");
        if (searchRadiusKm == null || !searchRadiusKm.isNumber()) {
            throw new GenericException(ExceptionCodeEnum.FORMAT_ERROR, "EMERGENCY_SETTINGS config is missing a numeric searchRadiusKm.");
        }
        return searchRadiusKm.asDouble();
    }

    /**
     * STUB (Prompt 82) - Phase 12's PresenceTracker doesn't exist yet, so every VERIFIED,
     * in-radius caretaker is unconditionally treated as online/reachable. MUST be rewired
     * once Phase 12 lands (e.g. {@code presenceTracker.isOnline(caretaker.getUserId())}) -
     * leaving this as an unconditional true past that point means the emergency fan-out
     * keeps "matching" caretakers who aren't actually available to respond.
     */
    private boolean isOnline(CaretakerProfile caretaker) {
        return true;
    }

    // Mirrors the repository's own native-SQL haversine computation in plain Java, purely
    // to label each match with the distance that produced it (Prompt 83's audit trail) -
    // the repository query is still what performs the actual filter/ordering.
    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
