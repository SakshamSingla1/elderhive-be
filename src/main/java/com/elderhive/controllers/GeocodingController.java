package com.elderhive.controllers;

import com.elderhive.geocoding.GeocodingResult;
import com.elderhive.geocoding.GeocodingService;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt 104's own prerequisite: a thin wrapper so the frontend address-autocomplete
 * component (molecules/AddressAutocomplete) never needs to know whether Mapbox or the free
 * Nominatim fallback is actually answering - GeocodingResult is already shaped exactly as
 * the API response needs (displayName + lat/long), so it's returned directly rather than
 * through a redundant parallel DTO.
 */
@RestController
@RequestMapping("/api/v1/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/search")
    public ResponseEntity<ResponseModel<List<GeocodingResult>>> search(@RequestParam String query) {
        return ApiResponse.successResponse(geocodingService.forwardGeocode(query));
    }
}
