package com.elderhive.dtos.Landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The public landing page's aggregate payload - GET /api/v1/landing/page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingPageResponse {

    // Null when no admin has ever saved the config yet - the public FE falls back to its
    // own built-in default copy in that case (see Landing.page.tsx), same as a brand-new
    // environment with none of the lists below populated either.
    private LandingPageConfigResponse config;
    private List<LandingFeatureResponse> features;
    private List<LandingFaqResponse> faqs;
    private List<LandingTestimonialResponse> testimonials;
}
