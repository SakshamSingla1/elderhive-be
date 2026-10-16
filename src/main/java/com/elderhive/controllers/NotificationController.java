package com.elderhive.controllers;

import com.elderhive.dtos.Notification.NotificationResponse;
import com.elderhive.dtos.Notification.NotificationTestRequest;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NotificationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET/PUT below resolve to the caller's own inbox (the established currentUser()
 * SecurityContextHolder pattern) — there is no "view another user's notifications" surface
 * anywhere here, including for admins.
 *
 * <p>POST /test is TEMPORARY (Prompt 59): the only way to manually exercise
 * NotificationService.send() end to end before Phase 10 (bookings) and Phase 11 (emergency
 * alerts) wire up real callers. SUPER_ADMIN-gated since it lets the caller send an arbitrary
 * seeded template to an arbitrary user — not something even a regular ADMIN should be able
 * to do. Remove this endpoint (or at minimum tighten it further) once Phase 10/11 land their
 * own real call sites into NotificationService.send() — do not let this stub linger as a
 * permanent "send any notification to any user" admin backdoor.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<NotificationResponse>>> getMine(Pageable pageable)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(notificationService.getByUser(caller.getId(), pageable),
                "Notifications fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread-count")
    public ResponseEntity<ResponseModel<Long>> getUnreadCount() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(notificationService.getUnreadCount(caller.getId()),
                "Unread count fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/read")
    public ResponseEntity<ResponseModel<String>> markAsRead(@PathVariable Long id) throws GenericException {
        User caller = currentUser();
        notificationService.markAsRead(id, caller.getId());
        return ApiResponse.successResponse("Marked", "Notification marked as read");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/read-all")
    public ResponseEntity<ResponseModel<String>> markAllAsRead() throws GenericException {
        User caller = currentUser();
        notificationService.markAllAsRead(caller.getId());
        return ApiResponse.successResponse("Marked", "All notifications marked as read");
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/test")
    public ResponseEntity<ResponseModel<String>> sendTest(@Valid @RequestBody NotificationTestRequest request)
            throws GenericException {
        notificationService.send(request.getTemplateKey(), request.getVariables(), request.getTargetUserId());
        return ApiResponse.successResponse("Sent", "Test notification sent successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
