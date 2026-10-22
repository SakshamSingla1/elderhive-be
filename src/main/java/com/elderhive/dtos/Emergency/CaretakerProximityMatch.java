package com.elderhive.dtos.Emergency;

import com.elderhive.entities.CaretakerProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One proximity-matched caretaker (Prompt 82) - carries the full {@link CaretakerProfile}
 * (Prompt 83's fan-out needs {@code getUserId()} off it to notify the right account) plus
 * the great-circle distance actually used to match/order it, so Prompt 83's audit log can
 * record exactly how far away each notified caretaker was.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerProximityMatch {

    private CaretakerProfile caretakerProfile;
    private double distanceKm;
}
