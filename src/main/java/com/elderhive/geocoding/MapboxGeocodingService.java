package com.elderhive.geocoding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
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
 * Real geocoding via Mapbox's Geocoding API v6 (GeoJSON FeatureCollection responses). The
 * GeocodingService bean whenever app.geocoding.enabled=true - the only other implementation,
 * NominatimGeocodingService, is excluded by the same property so exactly one bean is ever
 * active. Best-effort by design, same contract as the Nominatim fallback: neither method
 * ever throws, a failure degrades to null/empty.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.geocoding.enabled", havingValue = "true")
public class MapboxGeocodingService implements GeocodingService {

    private static final String FORWARD_URL = "https://api.mapbox.com/search/geocode/v6/forward";
    private static final String REVERSE_URL = "https://api.mapbox.com/search/geocode/v6/reverse";

    private final RestTemplate restTemplate;
    private final String accessToken;

    public MapboxGeocodingService(RestTemplateBuilder restTemplateBuilder,
                                   @Value("${app.geocoding.mapbox.access-token}") String accessToken) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.accessToken = accessToken;
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
                    .queryParam("longitude", longitude)
                    .queryParam("latitude", latitude)
                    .queryParam("access_token", accessToken)
                    .toUriString();
            List<Map<String, Object>> features = extractFeatures(exchange(url));
            return features.isEmpty() ? null : addressFromFeature(features.get(0));
        } catch (Exception e) {
            log.warn("Mapbox reverse geocoding failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    @Override
    public List<GeocodingResult> forwardGeocode(String addressText) {
        if (addressText == null || addressText.isBlank()) {
            return List.of();
        }
        try {
            String url = UriComponentsBuilder.fromUriString(FORWARD_URL)
                    .queryParam("q", addressText)
                    .queryParam("access_token", accessToken)
                    .queryParam("limit", 5)
                    .toUriString();
            return extractFeatures(exchange(url)).stream()
                    .map(this::toResult)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Mapbox forward geocoding failed for \"{}\": {}", addressText, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> exchange(String url) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFeatures(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object features = body.get("features");
        return features instanceof List ? (List<Map<String, Object>>) features : List.of();
    }

    @SuppressWarnings("unchecked")
    private GeocodingResult toResult(Map<String, Object> feature) {
        Object geometryObj = feature.get("geometry");
        if (!(geometryObj instanceof Map)) {
            return null;
        }
        Object coordsObj = ((Map<String, Object>) geometryObj).get("coordinates");
        if (!(coordsObj instanceof List) || ((List<Object>) coordsObj).size() < 2) {
            return null;
        }
        List<Object> coords = (List<Object>) coordsObj;
        double longitude = ((Number) coords.get(0)).doubleValue();
        double latitude = ((Number) coords.get(1)).doubleValue();
        String address = addressFromFeature(feature);
        return address != null ? new GeocodingResult(address, latitude, longitude) : null;
    }

    @SuppressWarnings("unchecked")
    private String addressFromFeature(Map<String, Object> feature) {
        Object propertiesObj = feature.get("properties");
        if (!(propertiesObj instanceof Map)) {
            return null;
        }
        Map<String, Object> properties = (Map<String, Object>) propertiesObj;
        Object fullAddress = properties.get("full_address");
        if (fullAddress != null) {
            return fullAddress.toString();
        }
        Object name = properties.get("name");
        return name != null ? name.toString() : null;
    }
}
