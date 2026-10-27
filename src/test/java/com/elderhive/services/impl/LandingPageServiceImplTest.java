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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandingPageServiceImplTest {

    @Mock
    private LandingPageConfigRepository landingPageConfigRepository;
    @Mock
    private LandingFeatureRepository landingFeatureRepository;
    @Mock
    private LandingFaqRepository landingFaqRepository;
    @Mock
    private LandingTestimonialRepository landingTestimonialRepository;

    @InjectMocks
    private LandingPageServiceImpl service;

    @Test
    void getPublicPageReturnsNullConfigWhenNoneHasEverBeenSaved() {
        when(landingPageConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(landingFeatureRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(landingFaqRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(landingTestimonialRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        LandingPageResponse response = service.getPublicPage();

        assertThat(response.getConfig()).isNull();
        assertThat(response.getFeatures()).isEmpty();
    }

    @Test
    void getPublicPageOnlyReturnsActiveEntries() {
        when(landingPageConfigRepository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(LandingPageConfig.builder().id(1L).heroHeadline("Welcome").build()));
        when(landingFeatureRepository.findByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(LandingFeature.builder().id(1L).title("Booking").isActive(true).build()));
        when(landingFaqRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(landingTestimonialRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        LandingPageResponse response = service.getPublicPage();

        assertThat(response.getConfig().getHeroHeadline()).isEqualTo("Welcome");
        assertThat(response.getFeatures()).extracting(LandingFeatureResponse::getTitle).containsExactly("Booking");
        verify(landingFeatureRepository, never()).findAllByOrderBySortOrderAsc();
    }

    @Test
    void updateConfigCreatesTheRowOnFirstSave() {
        when(landingPageConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(landingPageConfigRepository.save(any(LandingPageConfig.class))).thenAnswer(invocation -> {
            LandingPageConfig saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        LandingPageConfigRequest request = LandingPageConfigRequest.builder().heroHeadline("Hello").ctaButtonText("Join").build();
        LandingPageConfigResponse response = service.updateConfig(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getHeroHeadline()).isEqualTo("Hello");
        assertThat(response.getCtaButtonText()).isEqualTo("Join");
    }

    @Test
    void updateConfigUpdatesTheExistingRowRatherThanCreatingASecondOne() {
        LandingPageConfig existing = LandingPageConfig.builder().id(5L).heroHeadline("Old").build();
        when(landingPageConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(landingPageConfigRepository.save(any(LandingPageConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LandingPageConfigResponse response = service.updateConfig(LandingPageConfigRequest.builder().heroHeadline("New").build());

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getHeroHeadline()).isEqualTo("New");
    }

    @Test
    void createFeaturePersistsAllFields() {
        when(landingFeatureRepository.save(any(LandingFeature.class))).thenAnswer(invocation -> {
            LandingFeature saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        LandingFeatureResponse response = service.createFeature(
                LandingFeatureRequest.builder().title("Emergency SOS").iconName("alert-triangle").sortOrder(1).isActive(true).build());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Emergency SOS");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void updateFeatureThrowsWhenMissing() {
        when(landingFeatureRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateFeature(99L, LandingFeatureRequest.builder().title("x").build()))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.LANDING_FEATURE_NOT_FOUND);
    }

    @Test
    void deleteFeatureThrowsWhenMissingAndNeverDeletes() {
        when(landingFeatureRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteFeature(99L))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.LANDING_FEATURE_NOT_FOUND);

        verify(landingFeatureRepository, never()).deleteById(any());
    }

    @Test
    void createFaqPersistsAllFields() {
        when(landingFaqRepository.save(any(LandingFaq.class))).thenAnswer(invocation -> {
            LandingFaq saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        LandingFaqResponse response = service.createFaq(
                LandingFaqRequest.builder().question("Is there a mobile app?").answer("Not yet.").isActive(true).build());

        assertThat(response.getQuestion()).isEqualTo("Is there a mobile app?");
    }

    @Test
    void updateFaqThrowsWhenMissing() {
        when(landingFaqRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateFaq(99L, LandingFaqRequest.builder().question("q").answer("a").build()))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.LANDING_FAQ_NOT_FOUND);
    }

    @Test
    void createTestimonialPersistsAllFields() {
        when(landingTestimonialRepository.save(any(LandingTestimonial.class))).thenAnswer(invocation -> {
            LandingTestimonial saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        LandingTestimonialResponse response = service.createTestimonial(
                LandingTestimonialRequest.builder().authorName("Priya").content("Great platform").rating(5).isActive(true).build());

        assertThat(response.getAuthorName()).isEqualTo("Priya");
        assertThat(response.getRating()).isEqualTo(5);
    }

    @Test
    void updateTestimonialThrowsWhenMissing() {
        when(landingTestimonialRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTestimonial(99L, LandingTestimonialRequest.builder().authorName("x").content("y").build()))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.LANDING_TESTIMONIAL_NOT_FOUND);
    }

    @Test
    void deleteTestimonialThrowsWhenMissingAndNeverDeletes() {
        when(landingTestimonialRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteTestimonial(99L))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.LANDING_TESTIMONIAL_NOT_FOUND);

        verify(landingTestimonialRepository, never()).deleteById(any());
    }
}
