package com.elderhive.services;

import com.elderhive.dtos.Landing.LandingFaqRequest;
import com.elderhive.dtos.Landing.LandingFaqResponse;
import com.elderhive.dtos.Landing.LandingFeatureRequest;
import com.elderhive.dtos.Landing.LandingFeatureResponse;
import com.elderhive.dtos.Landing.LandingPageConfigRequest;
import com.elderhive.dtos.Landing.LandingPageConfigResponse;
import com.elderhive.dtos.Landing.LandingPageResponse;
import com.elderhive.dtos.Landing.LandingTestimonialRequest;
import com.elderhive.dtos.Landing.LandingTestimonialResponse;
import com.elderhive.exceptions.GenericException;

import java.util.List;

/**
 * A CMS-lite for the public marketing landing page (Prompt 99) - kept deliberately
 * simple, matching the prompt's own explicit scope: this is marketing content, not core
 * product. {@link #getPublicPage()} is the only public (unauthenticated) method; every
 * other method here is LANDING_MANAGE-gated at the controller.
 */
public interface LandingPageService {

    /** Hero/CTA config (null if never saved) plus every ACTIVE feature/FAQ/testimonial,
     *  each ordered by sortOrder. Cached - see LandingPageServiceImpl's own cache name. */
    LandingPageResponse getPublicPage();

    /** Upserts the single config row - creates it on the very first call, updates it on
     *  every call after. */
    LandingPageConfigResponse updateConfig(LandingPageConfigRequest request);

    LandingFeatureResponse createFeature(LandingFeatureRequest request);

    /** Every feature regardless of isActive - the admin editor's own listing. */
    List<LandingFeatureResponse> getAllFeatures();

    LandingFeatureResponse updateFeature(Long id, LandingFeatureRequest request) throws GenericException;

    void deleteFeature(Long id) throws GenericException;

    LandingFaqResponse createFaq(LandingFaqRequest request);

    List<LandingFaqResponse> getAllFaqs();

    LandingFaqResponse updateFaq(Long id, LandingFaqRequest request) throws GenericException;

    void deleteFaq(Long id) throws GenericException;

    LandingTestimonialResponse createTestimonial(LandingTestimonialRequest request);

    List<LandingTestimonialResponse> getAllTestimonials();

    LandingTestimonialResponse updateTestimonial(Long id, LandingTestimonialRequest request) throws GenericException;

    void deleteTestimonial(Long id) throws GenericException;
}
