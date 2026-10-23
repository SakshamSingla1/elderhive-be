package com.elderhive.services;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CaretakerProfileService {

    CaretakerProfileResponse createOrUpdate(User caller, CaretakerProfileRequest request) throws GenericException;

    CaretakerProfileResponse getByUserId(Long userId) throws GenericException;

    CaretakerProfileResponse getById(Long id) throws GenericException;

    Page<CaretakerProfileResponse> getAll(Pageable pageable);

    /**
     * Recomputes and persists this caretaker's denormalized ratingAverage from every
     * Review currently on file for them (a plain application-code aggregate query, not a
     * DB trigger - Prompt 88's own explicit instruction). Called by
     * ReviewServiceImpl right after a new review is saved; a caretaker with zero reviews
     * resets to 0.0 rather than being left stale or null.
     */
    void recomputeRatingAverage(Long caretakerId) throws GenericException;
}
