package com.elderhive.dtos.Search;

import com.elderhive.enums.CaretakerVerificationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deliberately lighter than CaretakerProfileResponse (Prompt 64) - just enough for a
 *  debounced autocomplete list item, not a full profile view. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerSearchResult {
    private Long id;
    private String name;
    private String specialtiesSummary;
    private Double ratingAverage;
    private Double distanceKm;
    private CaretakerVerificationStatusEnum verificationStatus;
}
