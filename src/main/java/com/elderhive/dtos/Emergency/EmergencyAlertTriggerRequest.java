package com.elderhive.dtos.Emergency;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlertTriggerRequest {

    @NotNull(message = "Elder profile is required")
    private Long elderProfileId;

    // Required, unlike ElderSphere's own (optional) request - EmergencyAlert.latitude/
    // longitude are themselves NOT NULL columns (Prompt 81), so a real SOS press must
    // always carry real coordinates for the proximity match (Prompt 82) to run at all.
    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;
}
