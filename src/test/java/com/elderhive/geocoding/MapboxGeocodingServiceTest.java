package com.elderhive.geocoding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Stubs Mapbox's own HTTP responses (never a real account/network call, via
 * MockRestServiceServer bound to the service's own internal RestTemplate) to prove the
 * GeoJSON FeatureCollection parsing is correct for both directions.
 */
class MapboxGeocodingServiceTest {

    private static final String FORWARD_RESPONSE = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [77.5946, 12.9716]},
                  "properties": {"full_address": "MG Road, Bengaluru, India", "name": "MG Road"}
                }
              ]
            }
            """;

    private static final String REVERSE_RESPONSE = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [77.5946, 12.9716]},
                  "properties": {"full_address": "MG Road, Bengaluru, India"}
                }
              ]
            }
            """;

    private MapboxGeocodingService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new MapboxGeocodingService(new RestTemplateBuilder(), "test-token");
        server = MockRestServiceServer.bindTo(service.restTemplateForTests()).build();
    }

    @Test
    void forwardGeocodeParsesCoordinatesAndFullAddressFromTheFirstMatchingFeature() {
        server.expect(requestTo(startsWith("https://api.mapbox.com/search/geocode/v6/forward")))
                .andRespond(withSuccess(FORWARD_RESPONSE, MediaType.APPLICATION_JSON));

        List<GeocodingResult> results = service.forwardGeocode("MG Road");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).displayName()).isEqualTo("MG Road, Bengaluru, India");
        assertThat(results.get(0).latitude()).isEqualTo(12.9716);
        assertThat(results.get(0).longitude()).isEqualTo(77.5946);
        server.verify();
    }

    @Test
    void reverseGeocodeReturnsTheFirstFeaturesFullAddress() {
        server.expect(requestTo(startsWith("https://api.mapbox.com/search/geocode/v6/reverse")))
                .andRespond(withSuccess(REVERSE_RESPONSE, MediaType.APPLICATION_JSON));

        String address = service.reverseGeocode(12.9716, 77.5946);

        assertThat(address).isEqualTo("MG Road, Bengaluru, India");
        server.verify();
    }

    @Test
    void forwardGeocodeDegradesToEmptyListOnServerError() {
        server.expect(requestTo(startsWith("https://api.mapbox.com/search/geocode/v6/forward")))
                .andRespond(withServerError());

        assertThat(service.forwardGeocode("anywhere")).isEmpty();
    }

    @Test
    void reverseGeocodeReturnsNullForNullCoordinatesWithoutMakingARequest() {
        assertThat(service.reverseGeocode(null, null)).isNull();
    }
}
