package com.elderhive.controllers;

import com.elderhive.dtos.Notification.NotificationTestRequest;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY (Prompt 59): the only way to manually exercise NotificationService.send() end to
 * end before Phase 10 (bookings) and Phase 11 (emergency alerts) wire up real callers.
 * SUPER_ADMIN-gated since it lets the caller send an arbitrary seeded template to an
 * arbitrary user — not something even a regular ADMIN should be able to do. Remove this
 * endpoint (or at minimum tighten it further) once Phase 10/11 land their own real call
 * sites into NotificationService.send() — do not let this stub linger as a permanent
 * "send any notification to any user" admin backdoor.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/test")
    public ResponseEntity<ResponseModel<String>> sendTest(@Valid @RequestBody NotificationTestRequest request)
            throws GenericException {
        notificationService.send(request.getTemplateKey(), request.getVariables(), request.getTargetUserId());
        return ApiResponse.successResponse("Sent", "Test notification sent successfully");
    }
}
