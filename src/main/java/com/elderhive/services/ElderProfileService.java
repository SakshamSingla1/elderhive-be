package com.elderhive.services;

import com.elderhive.dtos.ElderProfile.ElderProfileRequest;
import com.elderhive.dtos.ElderProfile.ElderProfileResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ElderProfileService {

    ElderProfileResponse create(ElderProfileRequest request) throws GenericException;

    /** Throws FORBIDDEN unless caller is the profile's family owner, its linked elder, or ADMIN/SUPER_ADMIN. */
    ElderProfileResponse update(Long id, ElderProfileRequest request, User caller) throws GenericException;

    /** Same access rule as update(). */
    ElderProfileResponse getById(Long id, User caller) throws GenericException;

    List<ElderProfileResponse> getByFamilyUserId(Long familyUserId);

    Page<ElderProfileResponse> getByFamilyUserId(Long familyUserId, Pageable pageable);

    ElderProfileResponse getByElderUserId(Long elderUserId) throws GenericException;

    /** ADMIN/SUPER_ADMIN only — enforced by the controller's @PreAuthorize, not re-checked here. */
    Page<ElderProfileResponse> getAll(Pageable pageable);

    /** Same access rule as update(). */
    void delete(Long id, User caller) throws GenericException;
}
