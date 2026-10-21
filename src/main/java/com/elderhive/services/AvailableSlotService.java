package com.elderhive.services;

import com.elderhive.dtos.AvailableSlot.AvailableSlotsResponse;
import com.elderhive.exceptions.GenericException;

import java.time.LocalDate;

public interface AvailableSlotService {

    /** Public - used by family members picking a time when booking a caretaker. */
    AvailableSlotsResponse getAvailableSlots(Long caretakerId, LocalDate date, Long serviceOfferingId) throws GenericException;
}
