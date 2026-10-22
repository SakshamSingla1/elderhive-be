package com.elderhive.controllers;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Just the trigger endpoint (Prompt 83) - claim/acknowledge/resolve/dispatch-override and
 * any read-back (getById/search) endpoints are Prompt 84's job, same incremental pattern
 * every other Phase 11 prompt has followed so far.
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

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
