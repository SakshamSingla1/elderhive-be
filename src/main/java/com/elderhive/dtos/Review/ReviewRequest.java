package com.elderhive.dtos.Review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @NotNull(message = "Booking is required")
    private Long bookingId;

    @NotNull(message = "Overall rating is required")
    @Min(value = 1, message = "Overall rating must be at least 1")
    @Max(value = 5, message = "Overall rating must be at most 5")
    private Integer overallRating;

    private String comment;

    // Optional rating breakdown - ReviewServiceImpl defaults each to overallRating when
    // omitted, rather than requiring a caller to fill in every sub-rating for a quick review.
    @Min(value = 1, message = "Punctuality rating must be at least 1")
    @Max(value = 5, message = "Punctuality rating must be at most 5")
    private Integer punctualityRating;

    @Min(value = 1, message = "Care quality rating must be at least 1")
    @Max(value = 5, message = "Care quality rating must be at most 5")
    private Integer careQualityRating;

    @Min(value = 1, message = "Communication rating must be at least 1")
    @Max(value = 5, message = "Communication rating must be at most 5")
    private Integer communicationRating;

    /** Optional, previously uploaded via the existing generic file-upload endpoint. */
    private Long photoFileAssetId;
}
