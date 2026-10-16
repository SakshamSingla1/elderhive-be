package com.elderhive.services.impl;

import com.elderhive.dtos.ElderProfile.ElderProfileRequest;
import com.elderhive.dtos.ElderProfile.ElderProfileResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.services.ElderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ported from ElderSphere's ElderProfileServiceImpl, trimmed to this prompt's scope: no
 * FamilyElderLink co-management (Prompt 62 — assertCanAccess only ever checks the single
 * family/elder owner columns this entity has today) and no invite/family-member endpoints
 * (Prompts 62/63).
 */
@Service
@RequiredArgsConstructor
public class ElderProfileServiceImpl implements ElderProfileService {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final ElderProfileRepository elderProfileRepository;

    @Override
    @Transactional
    public ElderProfileResponse create(ElderProfileRequest request) throws GenericException {
        ElderProfile profile = ElderProfile.builder()
                .familyUserId(request.getFamilyUserId())
                .elderUserId(request.getElderUserId())
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .medicalConditions(request.getMedicalConditions())
                .address(request.getAddress())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .build();
        return toResponse(elderProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public ElderProfileResponse update(Long id, ElderProfileRequest request, User caller) throws GenericException {
        ElderProfile profile = findOrThrow(id);
        assertCanAccess(profile, caller);
        profile.setFullName(request.getFullName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setGender(request.getGender());
        profile.setMedicalConditions(request.getMedicalConditions());
        profile.setAddress(request.getAddress());
        profile.setEmergencyContactName(request.getEmergencyContactName());
        profile.setEmergencyContactPhone(request.getEmergencyContactPhone());
        return toResponse(elderProfileRepository.save(profile));
    }

    @Override
    public ElderProfileResponse getById(Long id, User caller) throws GenericException {
        ElderProfile profile = findOrThrow(id);
        assertCanAccess(profile, caller);
        return toResponse(profile);
    }

    @Override
    public List<ElderProfileResponse> getByFamilyUserId(Long familyUserId) {
        return elderProfileRepository.findByFamilyUserId(familyUserId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ElderProfileResponse> getByFamilyUserId(Long familyUserId, Pageable pageable) {
        return elderProfileRepository.findByFamilyUserId(familyUserId, pageable).map(this::toResponse);
    }

    @Override
    public ElderProfileResponse getByElderUserId(Long elderUserId) throws GenericException {
        ElderProfile profile = elderProfileRepository.findByElderUserId(elderUserId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));
        return toResponse(profile);
    }

    @Override
    public Page<ElderProfileResponse> getAll(Pageable pageable) {
        return elderProfileRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id, User caller) throws GenericException {
        ElderProfile profile = findOrThrow(id);
        assertCanAccess(profile, caller);
        elderProfileRepository.deleteById(id);
    }

    private ElderProfile findOrThrow(Long id) throws GenericException {
        return elderProfileRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));
    }

    /** Only the linked family_user_id owner, the linked elder_user_id owner, or an ADMIN/SUPER_ADMIN may read or modify a profile. */
    private void assertCanAccess(ElderProfile profile, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return;
        }
        boolean isFamilyOwner = profile.getFamilyUserId() != null && Objects.equals(profile.getFamilyUserId(), caller.getId());
        boolean isElderOwner = profile.getElderUserId() != null && Objects.equals(profile.getElderUserId(), caller.getId());
        if (!isFamilyOwner && !isElderOwner) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this elder profile");
        }
    }

    private ElderProfileResponse toResponse(ElderProfile profile) {
        return ElderProfileResponse.builder()
                .id(profile.getId())
                .familyUserId(profile.getFamilyUserId())
                .elderUserId(profile.getElderUserId())
                .fullName(profile.getFullName())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .medicalConditions(profile.getMedicalConditions())
                .address(profile.getAddress())
                .emergencyContactName(profile.getEmergencyContactName())
                .emergencyContactPhone(profile.getEmergencyContactPhone())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .createdBy(profile.getCreatedBy())
                .updatedBy(profile.getUpdatedBy())
                .build();
    }
}
