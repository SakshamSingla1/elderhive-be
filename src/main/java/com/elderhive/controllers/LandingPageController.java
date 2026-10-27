package com.elderhive.controllers;

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
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.LandingPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /page} is the only unauthenticated route here (SecurityConfig permits it) -
 * the public marketing homepage. Every other endpoint is LANDING_MANAGE-gated, same single-
 * combined-permission shape as SERVICE_MANAGE/THEME_MANAGE elsewhere in this project
 * (unlike ElderSphere's split LANDING_MANAGEMENT_VIEW/LANDING_MANAGEMENT_MANAGE pair).
 */
@RestController
@RequestMapping("/api/v1/landing")
@RequiredArgsConstructor
public class LandingPageController {

    private final LandingPageService landingPageService;

    @GetMapping("/page")
    public ResponseEntity<ResponseModel<LandingPageResponse>> getPublicPage() {
        return ApiResponse.successResponse(landingPageService.getPublicPage(), "Landing page fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PutMapping("/config")
    public ResponseEntity<ResponseModel<LandingPageConfigResponse>> updateConfig(@Valid @RequestBody LandingPageConfigRequest request) {
        return ApiResponse.successResponse(landingPageService.updateConfig(request), "Landing page config updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PostMapping("/features")
    public ResponseEntity<ResponseModel<LandingFeatureResponse>> createFeature(@Valid @RequestBody LandingFeatureRequest request) {
        return ApiResponse.createSuccess(landingPageService.createFeature(request), "Feature created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @GetMapping("/features")
    public ResponseEntity<ResponseModel<List<LandingFeatureResponse>>> getAllFeatures() {
        return ApiResponse.successResponse(landingPageService.getAllFeatures(), "Features fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PutMapping("/features/{id}")
    public ResponseEntity<ResponseModel<LandingFeatureResponse>> updateFeature(
            @PathVariable Long id, @Valid @RequestBody LandingFeatureRequest request) throws GenericException {
        return ApiResponse.successResponse(landingPageService.updateFeature(id, request), "Feature updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @DeleteMapping("/features/{id}")
    public ResponseEntity<ResponseModel<String>> deleteFeature(@PathVariable Long id) throws GenericException {
        landingPageService.deleteFeature(id);
        return ApiResponse.successResponse("Feature deleted successfully", "Feature deleted successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PostMapping("/faqs")
    public ResponseEntity<ResponseModel<LandingFaqResponse>> createFaq(@Valid @RequestBody LandingFaqRequest request) {
        return ApiResponse.createSuccess(landingPageService.createFaq(request), "FAQ created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @GetMapping("/faqs")
    public ResponseEntity<ResponseModel<List<LandingFaqResponse>>> getAllFaqs() {
        return ApiResponse.successResponse(landingPageService.getAllFaqs(), "FAQs fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PutMapping("/faqs/{id}")
    public ResponseEntity<ResponseModel<LandingFaqResponse>> updateFaq(
            @PathVariable Long id, @Valid @RequestBody LandingFaqRequest request) throws GenericException {
        return ApiResponse.successResponse(landingPageService.updateFaq(id, request), "FAQ updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<ResponseModel<String>> deleteFaq(@PathVariable Long id) throws GenericException {
        landingPageService.deleteFaq(id);
        return ApiResponse.successResponse("FAQ deleted successfully", "FAQ deleted successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PostMapping("/testimonials")
    public ResponseEntity<ResponseModel<LandingTestimonialResponse>> createTestimonial(@Valid @RequestBody LandingTestimonialRequest request) {
        return ApiResponse.createSuccess(landingPageService.createTestimonial(request), "Testimonial created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @GetMapping("/testimonials")
    public ResponseEntity<ResponseModel<List<LandingTestimonialResponse>>> getAllTestimonials() {
        return ApiResponse.successResponse(landingPageService.getAllTestimonials(), "Testimonials fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @PutMapping("/testimonials/{id}")
    public ResponseEntity<ResponseModel<LandingTestimonialResponse>> updateTestimonial(
            @PathVariable Long id, @Valid @RequestBody LandingTestimonialRequest request) throws GenericException {
        return ApiResponse.successResponse(landingPageService.updateTestimonial(id, request), "Testimonial updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('LANDING_MANAGE')")
    @DeleteMapping("/testimonials/{id}")
    public ResponseEntity<ResponseModel<String>> deleteTestimonial(@PathVariable Long id) throws GenericException {
        landingPageService.deleteTestimonial(id);
        return ApiResponse.successResponse("Testimonial deleted successfully", "Testimonial deleted successfully");
    }
}
