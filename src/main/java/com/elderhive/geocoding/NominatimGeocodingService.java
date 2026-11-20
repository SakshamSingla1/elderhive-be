package com.elderhive.geocoding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Direct calls to the free, no-API-key OpenStreetMap Nominatim instance. The GeocodingService
 * bean whenever app.geocoding.enabled isn't explicitly "true" - keeps local dev and CI
 * runnable with zero Mapbox account. Never remove this fallback (IMPLEMENTATION_PLAN.md
 * Phase 16's own instruction) - MapboxGeocodingService is the alternative, selected by the
 * same property.
 *
 * <p>Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/)
 * requires a real contact identifier in the User-Agent of every request and caps traffic at
 * roughly 1 request/second - fine for this app's low-volume use cases (one reverse-geocode
 * per emergency alert, occasional address-autocomplete lookups), but this must never be
 * called in a tight loop.
 *
 * <p>Best-effort by design: neither method ever throws - a slow or failing geocoder must
 * never block triggering an emergency alert or break the autocomplete endpoint, so any
 * failure (timeout, non-2xx, malformed response) degrades to null/empty rather than
 * surfacing to the caller.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.geocoding.enabled", havingValue = "false", matchIfMissing = true)
public class NominatimGeocodingService implements GeocodingService {

    private static final String REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";

    private final RestTemplate restTemplate;
    private final String userAgent;

    public NominatimGeocodingService(RestTemplateBuilder restTemplateBuilder,
                                      @Value("${app.geocoding.contact-email:support@elderhive.app}") String contactEmail) {
        // Short timeouts - reverseGeocode sits directly in the critical path of triggering an
        // emergency alert, so a slow/unreachable Nominatim must fail fast, not stall it.
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.userAgent = "ElderHive/1.0 (" + contactEmail + ")";
    }

    /** Test-only seam for binding a MockRestServiceServer - not part of the public API. */
    RestTemplate restTemplateForTests() {
        return restTemplate;
    }

    @Override
    public String reverseGeocode(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            String url = UriComponentsBuilder.fromUriString(REVERSE_URL)
                    .queryParam("lat", latitude)
                    .queryParam("lon", longitude)
                    .queryParam("format", "jsonv2")
                    .toUriString();
            Map<String, Object> body = exchange(url, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            return body != null ? (String) body.get("display_name") : null;
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    @Override
    public List<GeocodingResult> forwardGeocode(String addressText) {
        if (addressText == null || addressText.isBlank()) {
            return List.of();
        }
        try {
            String url = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("q", addressText)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 5)
                    .toUriString();
            List<Map<String, Object>> body = exchange(url, new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
            if (body == null) {
                return List.of();
            }
            return body.stream().map(this::toResult).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("Forward geocoding failed for \"{}\": {}", addressText, e.getMessage());
            return List.of();
        }
    }

    private <T> T exchange(String url, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
        return response.getBody();
    }

    private GeocodingResult toResult(Map<String, Object> item) {
        Object displayName = item.get("display_name");
        Object lat = item.get("lat");
        Object lon = item.get("lon");
        if (displayName == null || lat == null || lon == null) {
            return null;
        }
        try {
            return new GeocodingResult(displayName.toString(),
                    Double.parseDouble(lat.toString()), Double.parseDouble(lon.toString()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
