package com.elderhive.dtos.Landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingPageConfigResponse {

    private Long id;
    private String heroHeadline;
    private String heroSubheadline;
    private String heroImageUrl;
    private String ctaHeadline;
    private String ctaDescription;
    private String ctaButtonText;
}
