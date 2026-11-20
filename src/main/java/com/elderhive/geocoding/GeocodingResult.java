package com.elderhive.geocoding;

/**
 * One forward-geocode candidate - a human-readable address paired with the coordinates it
 * resolves to, exactly what Prompt 104's frontend autocomplete needs to fill both the
 * display text and the underlying lat/long into a parent form.
 */
public record GeocodingResult(String displayName, Double latitude, Double longitude) {
}
