package com.elderhive.controllers;

import com.elderhive.dtos.Notification.NotificationPreferenceResponse;
import com.elderhive.dtos.Notification.NotificationPreferenceUpdateRequest;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Resolves to the caller's own preferences only (currentUser() pattern) - no admin
 *  "view/edit another user's preferences" surface exists here. */
@RestController
@RequestMapping("/api/v1/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<List<NotificationPreferenceResponse>>> getMine() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(notificationPreferenceService.getPreferences(caller.getId()),
                "Notification preferences fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ResponseEntity<ResponseModel<List<NotificationPreferenceResponse>>> updateMine(
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(
                notificationPreferenceService.updatePreferences(caller.getId(), request.getPreferences()),
                "Notification preferences updated successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
