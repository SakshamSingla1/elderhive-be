package com.elderhive.controllers;

import com.elderhive.dtos.ElderProfile.ElderProfileRequest;
import com.elderhive.dtos.ElderProfile.ElderProfileResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.ElderProfileService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the caller via the established SecurityContextHolder currentUser() pattern (not
 * ElderSphere's header-based Helper). Ownership of a created profile is always derived from
 * the caller's own identity server-side (resolveOwnership), never trusted from the request
 * body — an ADMIN/SUPER_ADMIN is the one exception, allowed to set familyUserId/elderUserId
 * explicitly to create a profile on behalf of a specific pair.
 */
@RestController
@RequestMapping("/api/v1/elder-profiles")
@RequiredArgsConstructor
public class ElderProfileController {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final ElderProfileService elderProfileService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<ElderProfileResponse>> create(@Valid @RequestBody ElderProfileRequest request)
            throws GenericException {
        User caller = currentUser();
        resolveOwnership(request, caller);
        return ApiResponse.createSuccess(elderProfileService.create(request), "Elder profile created successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ResponseModel<ElderProfileResponse>> getMyProfile() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(elderProfileService.getByElderUserId(caller.getId()),
                "Elder profile fetched successfully");
    }

    // Family members (and caretakers) see only their own; ADMIN/SUPER_ADMIN see every profile.
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ElderProfileResponse>>> getMine(Pageable pageable) throws GenericException {
        User caller = currentUser();
        Page<ElderProfileResponse> profiles = ADMIN_TIER.contains(caller.getUserType())
                ? elderProfileService.getAll(pageable)
                : elderProfileService.getByFamilyUserId(caller.getId(), pageable);
        return ApiResponse.successResponse(profiles, "Elder profiles fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<ElderProfileResponse>> getById(@PathVariable Long id) throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(elderProfileService.getById(id, caller), "Elder profile fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<ElderProfileResponse>> update(@PathVariable Long id,
                                                                        @Valid @RequestBody ElderProfileRequest request)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(elderProfileService.update(id, request, caller), "Elder profile updated successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        User caller = currentUser();
        elderProfileService.delete(id, caller);
        return ApiResponse.successResponse("Deleted", "Elder profile deleted successfully");
    }

    /**
     * An ELDER caller always creates a self-managed profile (elderUserId = self); any other
     * non-admin caller (FAMILY_MEMBER/CARETAKER) always creates a family-managed one
     * (familyUserId = self) — mirrors ElderSphere's own ElderProfileController branching
     * exactly. An ADMIN/SUPER_ADMIN caller's request values are left as-is, letting them
     * create a profile on behalf of a specific pair.
     */
    private void resolveOwnership(ElderProfileRequest request, User caller) {
        if (caller.getUserType() == UserTypeEnum.ELDER) {
            request.setElderUserId(caller.getId());
            request.setFamilyUserId(null);
        } else if (!ADMIN_TIER.contains(caller.getUserType())) {
            request.setFamilyUserId(caller.getId());
            request.setElderUserId(null);
        }
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
