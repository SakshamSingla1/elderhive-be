package com.elderhive.dtos.Landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingFeatureResponse {

    private Long id;
    private String title;
    private String description;
    private String iconName;
    private Integer sortOrder;
    private boolean isActive;
}
