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

class NominatimGeocodingServiceTest {

    private static final String SEARCH_RESPONSE = """
            [
              {"display_name": "MG Road, Bengaluru, India", "lat": "12.9716", "lon": "77.5946"}
            ]
            """;

    private static final String REVERSE_RESPONSE = """
            {"display_name": "MG Road, Bengaluru, India"}
            """;

    private NominatimGeocodingService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new NominatimGeocodingService(new RestTemplateBuilder(), "support@elderhive.app");
        server = MockRestServiceServer.bindTo(service.restTemplateForTests()).build();
    }

    @Test
    void forwardGeocodeParsesEveryMatchingResult() {
        server.expect(requestTo(startsWith("https://nominatim.openstreetmap.org/search")))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        List<GeocodingResult> results = service.forwardGeocode("MG Road");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).displayName()).isEqualTo("MG Road, Bengaluru, India");
        assertThat(results.get(0).latitude()).isEqualTo(12.9716);
        assertThat(results.get(0).longitude()).isEqualTo(77.5946);
        server.verify();
    }

    @Test
    void reverseGeocodeParsesTheDisplayName() {
        server.expect(requestTo(startsWith("https://nominatim.openstreetmap.org/reverse")))
                .andRespond(withSuccess(REVERSE_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(service.reverseGeocode(12.9716, 77.5946)).isEqualTo("MG Road, Bengaluru, India");
        server.verify();
    }

    @Test
    void forwardGeocodeDegradesToEmptyListOnServerError() {
        server.expect(requestTo(startsWith("https://nominatim.openstreetmap.org/search")))
                .andRespond(withServerError());

        assertThat(service.forwardGeocode("anywhere")).isEmpty();
    }

    @Test
    void forwardGeocodeReturnsEmptyListForBlankQueryWithoutMakingARequest() {
        assertThat(service.forwardGeocode("  ")).isEmpty();
    }
}
