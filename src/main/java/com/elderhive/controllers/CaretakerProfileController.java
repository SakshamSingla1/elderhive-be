package com.elderhive.controllers;

import com.elderhive.dtos.AvailableSlot.AvailableSlotsResponse;
import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityRequest;
import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityResponse;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.AvailableSlotService;
import com.elderhive.services.CaretakerAvailabilityService;
import com.elderhive.services.CaretakerProfileService;
import com.elderhive.services.CaretakerVerificationDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Unlike ElderProfileController, {@code /{id}} and the paged list are open to any
 * authenticated caller with no ownership check - a caretaker profile is a public-facing
 * directory listing (families browse caretakers), not private family data. Only
 * create/update is caretaker-self-only, enforced in the service layer.
 */
@RestController
@RequestMapping("/api/v1/caretaker-profiles")
@RequiredArgsConstructor
public class CaretakerProfileController {

    private final CaretakerProfileService caretakerProfileService;
    private final CaretakerVerificationDocumentService caretakerVerificationDocumentService;
    private final CaretakerAvailabilityService caretakerAvailabilityService;
    private final AvailableSlotService availableSlotService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<CaretakerProfileResponse>> createOrUpdate(@Valid @RequestBody CaretakerProfileRequest request)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(caretakerProfileService.createOrUpdate(caller, request),
                "Caretaker profile saved successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ResponseModel<CaretakerProfileResponse>> getMyProfile() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(caretakerProfileService.getByUserId(caller.getId()),
                "Caretaker profile fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<CaretakerProfileResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(caretakerProfileService.getById(id), "Caretaker profile fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<CaretakerProfileResponse>>> getAll(Pageable pageable) {
        return ApiResponse.successResponse(caretakerProfileService.getAll(pageable), "Caretaker profiles fetched successfully");
    }

    /** CARETAKER-only in effect, not by an explicit role check - the service resolves the
     *  caller's own profile by userId, so a caller with none yet (never a CARETAKER, or a
     *  CARETAKER who hasn't created one) gets CARETAKER_PROFILE_NOT_FOUND. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/me/verification-documents", consumes = "multipart/form-data")
    public ResponseEntity<ResponseModel<CaretakerVerificationDocumentResponse>> uploadVerificationDocument(
            @RequestParam("file") MultipartFile file, @RequestParam(value = "docType", required = false) String docType)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.createSuccess(caretakerVerificationDocumentService.uploadForCaller(caller, file, docType),
                "Verification document uploaded successfully");
    }

    /** Owner (the caretaker themselves) or an ADMIN/SUPER_ADMIN may view what's been submitted. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/verification-documents")
    public ResponseEntity<ResponseModel<List<CaretakerVerificationDocumentResponse>>> getVerificationDocuments(@PathVariable Long id)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(caretakerVerificationDocumentService.listForCaretaker(id, caller),
                "Verification documents fetched successfully");
    }

    /** CARETAKER-only in effect, via the same no-explicit-role-check pattern as upload above. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/availability")
    public ResponseEntity<ResponseModel<CaretakerAvailabilityResponse>> addAvailabilitySlot(
            @Valid @RequestBody CaretakerAvailabilityRequest request) throws GenericException {
        User caller = currentUser();
        return ApiResponse.createSuccess(caretakerAvailabilityService.create(caller, request), "Availability slot added successfully");
    }

    /** Public - used by family members browsing/booking a caretaker. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/availability")
    public ResponseEntity<ResponseModel<List<CaretakerAvailabilityResponse>>> getAvailability(@PathVariable Long id)
            throws GenericException {
        return ApiResponse.successResponse(caretakerAvailabilityService.getByCaretakerId(id), "Availability fetched successfully");
    }

    /** Owner (or ADMIN/SUPER_ADMIN) only - enforced in the service layer. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/availability/{slotId}")
    public ResponseEntity<ResponseModel<CaretakerAvailabilityResponse>> updateAvailabilitySlot(
            @PathVariable Long slotId, @Valid @RequestBody CaretakerAvailabilityRequest request) throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(caretakerAvailabilityService.update(slotId, caller, request),
                "Availability slot updated successfully");
    }

    /** Owner (or ADMIN/SUPER_ADMIN) only - enforced in the service layer. */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me/availability/{slotId}")
    public ResponseEntity<ResponseModel<String>> deleteAvailabilitySlot(@PathVariable Long slotId) throws GenericException {
        User caller = currentUser();
        caretakerAvailabilityService.delete(slotId, caller);
        return ApiResponse.successResponse("Deleted", "Availability slot deleted successfully");
    }

    /** Public - used by family members picking a time when booking a caretaker. Computed
     *  from CaretakerAvailability's weekly schedule (Prompt 66) minus Booking's (Prompt 75)
     *  already-booked times for that date - see AvailableSlotServiceImpl for the full
     *  algorithm, including the usingDefaultHours fallback. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/available-slots")
    public ResponseEntity<ResponseModel<AvailableSlotsResponse>> getAvailableSlots(
            @PathVariable Long id, @RequestParam LocalDate date, @RequestParam Long serviceOfferingId) throws GenericException {
        return ApiResponse.successResponse(availableSlotService.getAvailableSlots(id, date, serviceOfferingId),
                "Available slots fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
