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
    // Added Prompt 92 - lets a caretaker-facing search result be matched against
    // presence.PresenceService (keyed by user id, not this CaretakerProfile's own id)
    // without a second round trip per result.
    private Long userId;
    private String name;
    private String specialtiesSummary;
    private Double ratingAverage;
    private Double distanceKm;
    private CaretakerVerificationStatusEnum verificationStatus;
}
