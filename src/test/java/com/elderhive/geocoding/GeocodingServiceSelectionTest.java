package com.elderhive.geocoding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves app.geocoding.enabled selects exactly one GeocodingService bean -
 * EmergencyAlertServiceImpl never needs to know which implementation is active. No real
 * network calls; MapboxGeocodingServiceTest/NominatimGeocodingServiceTest cover actual
 * request/response parsing with a MockRestServiceServer.
 */
class GeocodingServiceSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NominatimGeocodingService.class, MapboxGeocodingService.class)
            .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
            .withPropertyValues("app.geocoding.mapbox.access-token=test-token");

    @Test
    void nominatimFallbackSelectedWhenPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GeocodingService.class);
            assertThat(context.getBean(GeocodingService.class)).isInstanceOf(NominatimGeocodingService.class);
        });
    }

    @Test
    void nominatimFallbackSelectedWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("app.geocoding.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(GeocodingService.class);
            assertThat(context.getBean(GeocodingService.class)).isInstanceOf(NominatimGeocodingService.class);
        });
    }

    @Test
    void mapboxBeanSelectedWhenEnabled() {
        contextRunner.withPropertyValues("app.geocoding.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(GeocodingService.class);
            assertThat(context.getBean(GeocodingService.class)).isInstanceOf(MapboxGeocodingService.class);
        });
    }
}
