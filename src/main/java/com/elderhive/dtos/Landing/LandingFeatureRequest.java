package com.elderhive.dtos.Landing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingFeatureRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String iconName;
    private Integer sortOrder;
    private boolean isActive;
}
