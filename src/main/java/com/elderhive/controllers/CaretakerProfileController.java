package com.elderhive.controllers;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileRequest;
import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
