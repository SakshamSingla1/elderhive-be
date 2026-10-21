package com.elderhive.services;

import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityRequest;
import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface CaretakerAvailabilityService {

    /** Adds one slot to the CALLER's own weekly schedule - there's no on-behalf-of path. */
    CaretakerAvailabilityResponse create(User caller, CaretakerAvailabilityRequest request) throws GenericException;

    /** Public - used by family members browsing/booking a caretaker. */
    List<CaretakerAvailabilityResponse> getByCaretakerId(Long caretakerId) throws GenericException;

    /** Caller must own the slot (or be ADMIN/SUPER_ADMIN). */
    CaretakerAvailabilityResponse update(Long id, User caller, CaretakerAvailabilityRequest request) throws GenericException;

    /** Caller must own the slot (or be ADMIN/SUPER_ADMIN). */
    void delete(Long id, User caller) throws GenericException;
}
