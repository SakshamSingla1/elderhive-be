package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityRequest;
import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityResponse;
import com.elderhive.entities.CaretakerAvailability;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerAvailabilityRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.services.CaretakerAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-row CRUD, not ElderSphere's own "replace the whole weekly list in one PUT" shape -
 * this prompt's text says "caretaker manages their own rows" (plural rows), which reads as
 * individual add/edit/remove, matching the CRUD-per-row convention every other entity in
 * this project already follows (ElderProfile, FamilyElderLink, ...) rather than a bulk
 * replace-all DTO.
 */
@Service
@RequiredArgsConstructor
public class CaretakerAvailabilityServiceImpl implements CaretakerAvailabilityService {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final CaretakerAvailabilityRepository availabilityRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;

    @Override
    @Transactional
    public CaretakerAvailabilityResponse create(User caller, CaretakerAvailabilityRequest request) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND,
                        "Create your caretaker profile before setting your availability"));
        assertValidRange(request);

        CaretakerAvailability slot = CaretakerAvailability.builder()
                .caretakerId(profile.getId())
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();
        return toResponse(availabilityRepository.save(slot));
    }

    @Override
    public List<CaretakerAvailabilityResponse> getByCaretakerId(Long caretakerId) throws GenericException {
        if (!caretakerProfileRepository.existsById(caretakerId)) {
            throw new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found");
        }
        return availabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(caretakerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CaretakerAvailabilityResponse update(Long id, User caller, CaretakerAvailabilityRequest request) throws GenericException {
        CaretakerAvailability slot = findOrThrow(id);
        assertOwnerOrAdmin(slot, caller);
        assertValidRange(request);

        slot.setDayOfWeek(request.getDayOfWeek());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        return toResponse(availabilityRepository.save(slot));
    }

    @Override
    @Transactional
    public void delete(Long id, User caller) throws GenericException {
        CaretakerAvailability slot = findOrThrow(id);
        assertOwnerOrAdmin(slot, caller);
        availabilityRepository.deleteById(id);
    }

    private CaretakerAvailability findOrThrow(Long id) throws GenericException {
        return availabilityRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Availability slot not found"));
    }

    private void assertOwnerOrAdmin(CaretakerAvailability slot, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return;
        }
        CaretakerProfile profile = caretakerProfileRepository.findByUserId(caller.getId()).orElse(null);
        boolean isOwner = profile != null && Objects.equals(profile.getId(), slot.getCaretakerId());
        if (!isOwner) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this availability slot");
        }
    }

    private void assertValidRange(CaretakerAvailabilityRequest request) throws GenericException {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new GenericException(ExceptionCodeEnum.VALIDATION_FAILED, "Start time must be before end time");
        }
    }

    private CaretakerAvailabilityResponse toResponse(CaretakerAvailability slot) {
        return CaretakerAvailabilityResponse.builder()
                .id(slot.getId())
                .caretakerId(slot.getCaretakerId())
                .dayOfWeek(slot.getDayOfWeek())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .createdAt(slot.getCreatedAt())
                .updatedAt(slot.getUpdatedAt())
                .build();
    }
}
