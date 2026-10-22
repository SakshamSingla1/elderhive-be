package com.elderhive.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;

/**
 * Direct call to the free, no-API-key OpenStreetMap Nominatim instance - a later phase
 * (see IMPLEMENTATION_PLAN.md, the geocoding-providers prompt) abstracts this behind a real
 * GeocodingService interface with a paid-provider implementation and this call as its
 * @ConditionalOnProperty fallback; until then, EmergencyAlertServiceImpl (Prompt 83) calls
 * this directly, exactly as that later prompt's own text expects to find it.
 *
 * <p>Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/)
 * requires a real contact identifier in the User-Agent of every request and caps traffic at
 * roughly 1 request/second - fine for this app's low-volume "resolve an emergency alert's
 * address" use case, but this must never be called in a tight loop.
 *
 * <p>Best-effort by design: reverseGeocode NEVER throws - a slow or failing geocoder must
 * never block triggering an emergency alert, so any failure (timeout, non-2xx, malformed
 * response) degrades to a null address rather than surfacing to the caller.
 */
@Slf4j
@Component
public class NominatimGeocodingClient {

    private static final String REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";

    private final RestTemplate restTemplate;
    private final String userAgent;

    public NominatimGeocodingClient(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${app.geocoding.contact-email:support@elderhive.app}") String contactEmail) {
        // Short timeouts - this call sits directly in the critical path of triggering an
        // emergency alert, so a slow/unreachable Nominatim must fail fast, not stall it.
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.userAgent = "ElderHive/1.0 (" + contactEmail + ")";
    }

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
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, userAgent);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> body = response.getBody();
            return body != null ? (String) body.get("display_name") : null;
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }
}
