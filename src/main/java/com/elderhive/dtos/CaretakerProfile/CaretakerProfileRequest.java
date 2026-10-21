package com.elderhive.dtos.CaretakerProfile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

/**
 * No userId/verificationStatus/profilePhotoFileAssetId/ratingAverage here - userId always
 * comes from the authenticated caller (never trusted from the body), verificationStatus is
 * admin-only (Prompt 65's review endpoint), profilePhotoFileAssetId is set by the upload
 * endpoint (Prompt 65), and ratingAverage is denormalized from Review rows (Phase 12) - none
 * of them are ever directly caretaker-editable.
 *
 * <p>{@code specialties} is a set of ServiceCategory ids (Prompt 72) - a caretaker picks
 * from the admin-managed catalog, not free text anymore.
 */
@Data
public class CaretakerProfileRequest {

    private String bio;

    private Set<Long> specialties;

    @Min(value = 0, message = "Years of experience cannot be negative")
    private Integer yearsOfExperience;

    @DecimalMin(value = "0.0", message = "Hourly rate cannot be negative")
    private BigDecimal hourlyRate;

    private Double latitude;

    private Double longitude;

    @DecimalMin(value = "0.0", message = "Service radius cannot be negative")
    private Double serviceRadiusKm;
}
