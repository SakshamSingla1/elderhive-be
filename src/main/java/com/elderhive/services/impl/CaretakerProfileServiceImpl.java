package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.CaretakerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

/**
 * Ported from ElderSphere's own createOrUpdateForUser/getByUserId/getById shape, upsert
 * semantics included (a caretaker calling create again just updates their existing row -
 * there's no separate update endpoint). Unlike ElderProfileServiceImpl, there's no
 * ADMIN-on-behalf-of path here - a caretaker profile has exactly one possible owner (the
 * caretaker themselves), so this prompt's text gives admins no reason to create one.
 */
@Service
@RequiredArgsConstructor
public class CaretakerProfileServiceImpl implements CaretakerProfileService {

    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CaretakerProfileResponse createOrUpdate(User caller, CaretakerProfileRequest request) throws GenericException {
        if (caller.getUserType() != UserTypeEnum.CARETAKER) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "Only a caretaker can manage a caretaker profile");
        }

        CaretakerProfile profile = caretakerProfileRepository.findByUserId(caller.getId()).orElse(null);
        if (profile == null) {
            profile = CaretakerProfile.builder()
                    .userId(caller.getId())
                    .verificationStatus(CaretakerVerificationStatusEnum.PENDING)
                    .ratingAverage(0.0)
                    .build();
        }

        profile.setBio(request.getBio());
        profile.setSpecialties(request.getSpecialties() != null ? new HashSet<>(request.getSpecialties()) : new HashSet<>());
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setHourlyRate(request.getHourlyRate());
        profile.setLatitude(request.getLatitude());
        profile.setLongitude(request.getLongitude());
        profile.setServiceRadiusKm(request.getServiceRadiusKm());

        return toResponse(caretakerProfileRepository.save(profile), caller);
    }

    @Override
    public CaretakerProfileResponse getByUserId(Long userId) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
        return toResponse(profile, userRepository.findById(userId).orElse(null));
    }

    @Override
    public CaretakerProfileResponse getById(Long id) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
        return toResponse(profile, userRepository.findById(profile.getUserId()).orElse(null));
    }

    @Override
    public Page<CaretakerProfileResponse> getAll(Pageable pageable) {
        return caretakerProfileRepository.findAll(pageable)
                .map(profile -> toResponse(profile, userRepository.findById(profile.getUserId()).orElse(null)));
    }

    private CaretakerProfileResponse toResponse(CaretakerProfile profile, User user) {
        return CaretakerProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(user != null ? user.getFullName() : null)
                .email(user != null ? user.getEmail() : null)
                .phone(user != null ? user.getPhone() : null)
                .bio(profile.getBio())
                .specialties(profile.getSpecialties())
                .yearsOfExperience(profile.getYearsOfExperience())
                .hourlyRate(profile.getHourlyRate())
                .ratingAverage(profile.getRatingAverage())
                .verificationStatus(profile.getVerificationStatus())
                .profilePhotoFileAssetId(profile.getProfilePhotoFileAssetId())
                .latitude(profile.getLatitude())
                .longitude(profile.getLongitude())
                .serviceRadiusKm(profile.getServiceRadiusKm())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .createdBy(profile.getCreatedBy())
                .updatedBy(profile.getUpdatedBy())
                .build();
    }
}
