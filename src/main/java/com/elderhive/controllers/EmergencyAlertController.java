package com.elderhive.controllers;

import com.elderhive.dtos.Emergency.EmergencyAlertDispatchOverrideRequest;
import com.elderhive.dtos.Emergency.EmergencyAlertResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertTriggerRequest;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.EmergencyAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger (Prompt 83) plus claim/resolve/dispatch-override (Prompt 84) - any read-back
 * (getById/search) endpoints still don't exist yet, same incremental pattern every other
 * Phase 11 prompt has followed so far.
 */
@RestController
@RequestMapping("/api/v1/emergency-alerts")
@RequiredArgsConstructor
public class EmergencyAlertController {

    private final EmergencyAlertService emergencyAlertService;
    private final UserRepository userRepository;

    // Only the two roles Prompt 85's frontend actually mounts the SOS button for - an
    // elder triggering their own alert, or a family member triggering one on an elder's
    // behalf. EmergencyAlertServiceImpl's own assertCanTrigger still checks that THIS
    // specific caller actually has access to THIS specific elder profile.
    @PreAuthorize("hasAnyRole('ELDER','FAMILY_MEMBER')")
    @PostMapping
    public ResponseEntity<ResponseModel<EmergencyAlertResponse>> trigger(@Valid @RequestBody EmergencyAlertTriggerRequest request) throws GenericException {
        return ApiResponse.createSuccess(emergencyAlertService.trigger(currentUser(), request), "Emergency alert triggered");
    }

    @PreAuthorize("hasRole('CARETAKER')")
    @PutMapping("/{id}/claim")
    public ResponseEntity<ResponseModel<EmergencyAlertResponse>> claim(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(emergencyAlertService.claim(id, currentUser()), "Emergency alert claimed");
    }

    // Broad role gate - EmergencyAlertServiceImpl#assertCanResolve does the actual
    // fine-grained check (the claiming caretaker specifically, or an admin/super-admin
    // with EMERGENCY_DISPATCH), the same split BookingController uses throughout.
    @PreAuthorize("hasAnyRole('CARETAKER','ADMIN','SUPER_ADMIN')")
    @PutMapping("/{id}/resolve")
    public ResponseEntity<ResponseModel<EmergencyAlertResponse>> resolve(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(emergencyAlertService.resolve(id, currentUser()), "Emergency alert resolved");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('EMERGENCY_DISPATCH')")
    @PutMapping("/{id}/dispatch-override")
    public ResponseEntity<ResponseModel<EmergencyAlertResponse>> dispatchOverride(
            @PathVariable Long id, @RequestBody EmergencyAlertDispatchOverrideRequest request) throws GenericException {
        return ApiResponse.successResponse(emergencyAlertService.dispatchOverride(id, currentUser(), request), "Emergency alert dispatch overridden");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
