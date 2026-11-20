package com.elderhive.geocoding;

import java.util.List;

/**
 * Both implementations (MapboxGeocodingService, NominatimGeocodingService) are best-effort
 * by design - neither method ever throws. reverseGeocode sits in EmergencyAlert's trigger
 * path (NominatimGeocodingClient's own original contract, preserved here) and forwardGeocode
 * backs Prompt 104's address-autocomplete endpoint - a slow/failing geocoder must degrade
 * to null/empty rather than blocking either caller.
 */
public interface GeocodingService {

    String reverseGeocode(Double latitude, Double longitude);

    List<GeocodingResult> forwardGeocode(String addressText);
}
