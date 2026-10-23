package com.elderhive.services.impl;

import com.elderhive.dtos.Emergency.CaretakerProximityMatch;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.presence.PresenceService;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.seed.MetaConfigSeeder;
import com.elderhive.services.AdminConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocks CaretakerProfileRepository - the real haversine filter/ordering it performs is
 * verified for real against Postgres in CaretakerProfileRepositoryTest. This test covers
 * what's actually this service's own job: resolving EMERGENCY_SETTINGS' searchRadiusKm
 * default, passing it through to the repository call, computing each match's own
 * distanceKm against known coordinates, and (since Prompt 90) the PresenceService-backed
 * online filter.
 */
@ExtendWith(MockitoExtension.class)
class ProximityMatchingServiceImplTest {

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;
    @Mock
    private AdminConfigService adminConfigService;
    // A real, functioning ObjectMapper (not a behavior mock) - @Spy still makes it a valid
    // constructor-injection candidate for @InjectMocks, which otherwise can't resolve
    // ProximityMatchingServiceImpl's third constructor parameter.
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private ProximityMatchingServiceImpl service;

    private CaretakerProfile caretaker(long id, double latitude, double longitude) {
        return CaretakerProfile.builder().id(id).userId(100 + id).latitude(latitude).longitude(longitude).build();
    }

    private void stubDefaultRadius(int searchRadiusKm) throws GenericException {
        when(adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT))
                .thenReturn(MetaConfigResponse.builder().context("EMERGENCY_SETTINGS")
                        .data("{\"searchRadiusKm\": " + searchRadiusKm + ", \"escalationTimeoutMinutes\": 15}").build());
    }

    @Test
    void readsTheDefaultRadiusFromEmergencySettingsAndPassesItToTheRepository() throws GenericException {
        stubDefaultRadius(10);
        when(caretakerProfileRepository.findVerifiedWithinRadius(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        service.findMatchingCaretakers(12.9716, 77.5946);

        verify(caretakerProfileRepository).findVerifiedWithinRadius(eq(12.9716), eq(77.5946), eq(10.0));
    }

    @Test
    void computesEachMatchsDistanceKmAgainstKnownCoordinatesAndPreservesRepositoryOrder() throws GenericException {
        stubDefaultRadius(10);
        // Same longitude as the alert - on a shared meridian the haversine formula
        // collapses to EARTH_RADIUS_KM * latitudeDeltaInRadians exactly, so these expected
        // distances are exact: 0.01deg ~= 1.1119km, 0.04deg ~= 4.4478km.
        CaretakerProfile near = caretaker(1L, 0.01, 0.0);
        CaretakerProfile mid = caretaker(2L, 0.04, 0.0);
        // Repository already returns its own results ordered nearest-first - the service
        // must not re-sort, so deliberately return them already in that order here.
        when(caretakerProfileRepository.findVerifiedWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(near, mid));
        when(presenceService.isOnline(101L)).thenReturn(true);
        when(presenceService.isOnline(102L)).thenReturn(true);

        List<CaretakerProximityMatch> matches = service.findMatchingCaretakers(0.0, 0.0);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getCaretakerProfile().getId()).isEqualTo(1L);
        assertThat(matches.get(0).getDistanceKm()).isCloseTo(1.1119, within(0.01));
        assertThat(matches.get(1).getCaretakerProfile().getId()).isEqualTo(2L);
        assertThat(matches.get(1).getDistanceKm()).isCloseTo(4.4478, within(0.01));
    }

    @Test
    void onlineCaretakersAreIncludedAndOfflineOnesAreExcluded() throws GenericException {
        stubDefaultRadius(10);
        when(caretakerProfileRepository.findVerifiedWithinRadius(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(caretaker(1L, 0.01, 0.0), caretaker(2L, 0.02, 0.0), caretaker(3L, 0.03, 0.0)));
        // userId = 100 + id (see the caretaker() helper above).
        when(presenceService.isOnline(101L)).thenReturn(true);
        when(presenceService.isOnline(102L)).thenReturn(false);
        when(presenceService.isOnline(103L)).thenReturn(true);

        List<CaretakerProximityMatch> matches = service.findMatchingCaretakers(0.0, 0.0);

        assertThat(matches).extracting(m -> m.getCaretakerProfile().getId()).containsExactly(1L, 3L);
    }

    @Test
    void missingSearchRadiusKmInTheConfigThrowsRatherThanGuessingADefault() throws GenericException {
        when(adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT))
                .thenReturn(MetaConfigResponse.builder().context("EMERGENCY_SETTINGS")
                        .data("{\"escalationTimeoutMinutes\": 15}").build());

        assertThatThrownBy(() -> service.findMatchingCaretakers(0.0, 0.0))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORMAT_ERROR);
    }

    @Test
    void malformedJsonInTheConfigThrowsRatherThanCrashingWithARawParseException() throws GenericException {
        when(adminConfigService.getConfig(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT))
                .thenReturn(MetaConfigResponse.builder().context("EMERGENCY_SETTINGS").data("not valid json").build());

        assertThatThrownBy(() -> service.findMatchingCaretakers(0.0, 0.0))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORMAT_ERROR);
    }
}
