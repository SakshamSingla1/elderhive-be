package com.elderhive.controllers;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.CaretakerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
