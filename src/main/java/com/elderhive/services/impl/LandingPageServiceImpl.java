package com.elderhive.services.impl;

import com.elderhive.dtos.Landing.LandingFaqRequest;
import com.elderhive.dtos.Landing.LandingFaqResponse;
import com.elderhive.dtos.Landing.LandingFeatureRequest;
import com.elderhive.dtos.Landing.LandingFeatureResponse;
import com.elderhive.dtos.Landing.LandingPageConfigRequest;
import com.elderhive.dtos.Landing.LandingPageConfigResponse;
import com.elderhive.dtos.Landing.LandingPageResponse;
import com.elderhive.dtos.Landing.LandingTestimonialRequest;
import com.elderhive.dtos.Landing.LandingTestimonialResponse;
import com.elderhive.entities.LandingFaq;
import com.elderhive.entities.LandingFeature;
import com.elderhive.entities.LandingPageConfig;
import com.elderhive.entities.LandingTestimonial;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.LandingFaqRepository;
import com.elderhive.repositories.LandingFeatureRepository;
import com.elderhive.repositories.LandingPageConfigRepository;
import com.elderhive.repositories.LandingTestimonialRepository;
import com.elderhive.services.LandingPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LandingPageServiceImpl implements LandingPageService {

    private static final String PUBLIC_PAGE_CACHE = "landingPagePublic";

    private final LandingPageConfigRepository landingPageConfigRepository;
    private final LandingFeatureRepository landingFeatureRepository;
    private final LandingFaqRepository landingFaqRepository;
    private final LandingTestimonialRepository landingTestimonialRepository;

    @Override
    @Cacheable(PUBLIC_PAGE_CACHE)
    public LandingPageResponse getPublicPage() {
        LandingPageConfig config = landingPageConfigRepository.findFirstByOrderByIdAsc().orElse(null);
        return LandingPageResponse.builder()
                .config(config != null ? toConfigResponse(config) : null)
                .features(landingFeatureRepository.findByIsActiveTrueOrderBySortOrderAsc().stream().map(this::toFeatureResponse).toList())
                .faqs(landingFaqRepository.findByIsActiveTrueOrderBySortOrderAsc().stream().map(this::toFaqResponse).toList())
                .testimonials(landingTestimonialRepository.findByIsActiveTrueOrderBySortOrderAsc().stream().map(this::toTestimonialResponse).toList())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingPageConfigResponse updateConfig(LandingPageConfigRequest request) {
        LandingPageConfig config = landingPageConfigRepository.findFirstByOrderByIdAsc().orElseGet(LandingPageConfig::new);
        config.setHeroHeadline(request.getHeroHeadline());
        config.setHeroSubheadline(request.getHeroSubheadline());
        config.setHeroImageUrl(request.getHeroImageUrl());
        config.setCtaHeadline(request.getCtaHeadline());
        config.setCtaDescription(request.getCtaDescription());
        config.setCtaButtonText(request.getCtaButtonText());
        return toConfigResponse(landingPageConfigRepository.save(config));
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingFeatureResponse createFeature(LandingFeatureRequest request) {
        LandingFeature feature = LandingFeature.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .iconName(request.getIconName())
                .sortOrder(request.getSortOrder())
                .isActive(request.isActive())
                .build();
        return toFeatureResponse(landingFeatureRepository.save(feature));
    }

    @Override
    public List<LandingFeatureResponse> getAllFeatures() {
        return landingFeatureRepository.findAllByOrderBySortOrderAsc().stream().map(this::toFeatureResponse).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingFeatureResponse updateFeature(Long id, LandingFeatureRequest request) throws GenericException {
        LandingFeature feature = landingFeatureRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.LANDING_FEATURE_NOT_FOUND, "Landing feature not found"));
        feature.setTitle(request.getTitle());
        feature.setDescription(request.getDescription());
        feature.setIconName(request.getIconName());
        feature.setSortOrder(request.getSortOrder());
        feature.setActive(request.isActive());
        return toFeatureResponse(landingFeatureRepository.save(feature));
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public void deleteFeature(Long id) throws GenericException {
        if (!landingFeatureRepository.existsById(id)) {
            throw new GenericException(ExceptionCodeEnum.LANDING_FEATURE_NOT_FOUND, "Landing feature not found");
        }
        landingFeatureRepository.deleteById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingFaqResponse createFaq(LandingFaqRequest request) {
        LandingFaq faq = LandingFaq.builder()
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .sortOrder(request.getSortOrder())
                .isActive(request.isActive())
                .build();
        return toFaqResponse(landingFaqRepository.save(faq));
    }

    @Override
    public List<LandingFaqResponse> getAllFaqs() {
        return landingFaqRepository.findAllByOrderBySortOrderAsc().stream().map(this::toFaqResponse).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingFaqResponse updateFaq(Long id, LandingFaqRequest request) throws GenericException {
        LandingFaq faq = landingFaqRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.LANDING_FAQ_NOT_FOUND, "Landing FAQ not found"));
        faq.setQuestion(request.getQuestion());
        faq.setAnswer(request.getAnswer());
        faq.setSortOrder(request.getSortOrder());
        faq.setActive(request.isActive());
        return toFaqResponse(landingFaqRepository.save(faq));
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public void deleteFaq(Long id) throws GenericException {
        if (!landingFaqRepository.existsById(id)) {
            throw new GenericException(ExceptionCodeEnum.LANDING_FAQ_NOT_FOUND, "Landing FAQ not found");
        }
        landingFaqRepository.deleteById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingTestimonialResponse createTestimonial(LandingTestimonialRequest request) {
        LandingTestimonial testimonial = LandingTestimonial.builder()
                .authorName(request.getAuthorName())
                .authorRole(request.getAuthorRole())
                .content(request.getContent())
                .avatarUrl(request.getAvatarUrl())
                .rating(request.getRating())
                .sortOrder(request.getSortOrder())
                .isActive(request.isActive())
                .build();
        return toTestimonialResponse(landingTestimonialRepository.save(testimonial));
    }

    @Override
    public List<LandingTestimonialResponse> getAllTestimonials() {
        return landingTestimonialRepository.findAllByOrderBySortOrderAsc().stream().map(this::toTestimonialResponse).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public LandingTestimonialResponse updateTestimonial(Long id, LandingTestimonialRequest request) throws GenericException {
        LandingTestimonial testimonial = landingTestimonialRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.LANDING_TESTIMONIAL_NOT_FOUND, "Landing testimonial not found"));
        testimonial.setAuthorName(request.getAuthorName());
        testimonial.setAuthorRole(request.getAuthorRole());
        testimonial.setContent(request.getContent());
        testimonial.setAvatarUrl(request.getAvatarUrl());
        testimonial.setRating(request.getRating());
        testimonial.setSortOrder(request.getSortOrder());
        testimonial.setActive(request.isActive());
        return toTestimonialResponse(landingTestimonialRepository.save(testimonial));
    }

    @Override
    @Transactional
    @CacheEvict(value = PUBLIC_PAGE_CACHE, allEntries = true)
    public void deleteTestimonial(Long id) throws GenericException {
        if (!landingTestimonialRepository.existsById(id)) {
            throw new GenericException(ExceptionCodeEnum.LANDING_TESTIMONIAL_NOT_FOUND, "Landing testimonial not found");
        }
        landingTestimonialRepository.deleteById(id);
    }

    private LandingPageConfigResponse toConfigResponse(LandingPageConfig config) {
        return LandingPageConfigResponse.builder()
                .id(config.getId())
                .heroHeadline(config.getHeroHeadline())
                .heroSubheadline(config.getHeroSubheadline())
                .heroImageUrl(config.getHeroImageUrl())
                .ctaHeadline(config.getCtaHeadline())
                .ctaDescription(config.getCtaDescription())
                .ctaButtonText(config.getCtaButtonText())
                .build();
    }

    private LandingFeatureResponse toFeatureResponse(LandingFeature feature) {
        return LandingFeatureResponse.builder()
                .id(feature.getId())
                .title(feature.getTitle())
                .description(feature.getDescription())
                .iconName(feature.getIconName())
                .sortOrder(feature.getSortOrder())
                .isActive(feature.isActive())
                .build();
    }

    private LandingFaqResponse toFaqResponse(LandingFaq faq) {
        return LandingFaqResponse.builder()
                .id(faq.getId())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .sortOrder(faq.getSortOrder())
                .isActive(faq.isActive())
                .build();
    }

    private LandingTestimonialResponse toTestimonialResponse(LandingTestimonial testimonial) {
        return LandingTestimonialResponse.builder()
                .id(testimonial.getId())
                .authorName(testimonial.getAuthorName())
                .authorRole(testimonial.getAuthorRole())
                .content(testimonial.getContent())
                .avatarUrl(testimonial.getAvatarUrl())
                .rating(testimonial.getRating())
                .sortOrder(testimonial.getSortOrder())
                .isActive(testimonial.isActive())
                .build();
    }
}
