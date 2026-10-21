package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerProfileServiceImplTest {

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CaretakerProfileServiceImpl caretakerProfileService;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private CaretakerProfileRequest request() {
        CaretakerProfileRequest request = new CaretakerProfileRequest();
        request.setBio("Experienced caregiver");
        request.setSpecialties(Set.of("Mobility Assistance"));
        request.setYearsOfExperience(5);
        request.setHourlyRate(new BigDecimal("25.00"));
        request.setLatitude(12.9716);
        request.setLongitude(77.5946);
        request.setServiceRadiusKm(10.0);
        return request;
    }

    @Test
    void aCaretakerCreatingAProfileForTheFirstTimeDefaultsToPendingAndZeroRating() throws GenericException {
        User caretaker = user(1L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> {
            CaretakerProfile p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        CaretakerProfileResponse response = caretakerProfileService.createOrUpdate(caretaker, request());

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getVerificationStatus()).isEqualTo(CaretakerVerificationStatusEnum.PENDING);
        assertThat(response.getRatingAverage()).isEqualTo(0.0);
        assertThat(response.getLatitude()).isEqualTo(12.9716);
        assertThat(response.getLongitude()).isEqualTo(77.5946);
        assertThat(response.getServiceRadiusKm()).isEqualTo(10.0);
        assertThat(response.getSpecialties()).containsExactly("Mobility Assistance");
        assertThat(response.getFullName()).isEqualTo("User 1");
    }

    @Test
    void callingCreateAgainUpdatesTheExistingProfileInPlace() throws GenericException {
        User caretaker = user(1L, UserTypeEnum.CARETAKER);
        CaretakerProfile existing = CaretakerProfile.builder()
                .id(100L).userId(1L).bio("Old bio").verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).ratingAverage(4.5).build();
        when(caretakerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaretakerProfileResponse response = caretakerProfileService.createOrUpdate(caretaker, request());

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getBio()).isEqualTo("Experienced caregiver");
        // Verification status and rating are untouched by an update - not caretaker-editable.
        assertThat(response.getVerificationStatus()).isEqualTo(CaretakerVerificationStatusEnum.VERIFIED);
        assertThat(response.getRatingAverage()).isEqualTo(4.5);
    }

    @Test
    void aNonCaretakerCannotCreateOrUpdateAProfile() {
        User family = user(1L, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> caretakerProfileService.createOrUpdate(family, request()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(caretakerProfileRepository, never()).save(any());
    }

    @Test
    void getByUserIdReturnsTheProfileEnrichedWithUserDetails() throws GenericException {
        CaretakerProfile profile = CaretakerProfile.builder()
                .id(100L).userId(1L).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build();
        when(caretakerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserTypeEnum.CARETAKER)));

        CaretakerProfileResponse response = caretakerProfileService.getByUserId(1L);

        assertThat(response.getFullName()).isEqualTo("User 1");
        assertThat(response.getEmail()).isEqualTo("user1@example.com");
    }

    @Test
    void getByUserIdThrowsNotFoundWhenNoProfileExists() {
        when(caretakerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caretakerProfileService.getByUserId(1L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownId() {
        when(caretakerProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caretakerProfileService.getById(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void getAllReturnsEveryProfileEnrichedWithUserDetails() {
        CaretakerProfile profile = CaretakerProfile.builder().id(100L).userId(1L).build();
        when(caretakerProfileRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserTypeEnum.CARETAKER)));

        var page = caretakerProfileService.getAll(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getFullName()).isEqualTo("User 1");
    }
}
