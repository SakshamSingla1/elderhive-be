package com.elderhive.dtos.MetaConfig;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Raw JSON string, opaque to the backend — the admin UI owns validating/shaping it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaConfigRequest {

    @NotBlank(message = "data is required")
    private String data;
}
