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
}
