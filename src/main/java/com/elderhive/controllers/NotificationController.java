package com.elderhive.controllers;

import com.elderhive.dtos.Notification.NotificationResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolves to the caller's own inbox (the established currentUser() SecurityContextHolder
 * pattern) — there is no "view another user's notifications" surface anywhere here,
 * including for admins.
 *
 * <p>Prompt 59's temporary POST /test stub (a SUPER_ADMIN-only "send any seeded template to
 * any user" backdoor, kept only until a real caller existed) is gone - BookingServiceImpl
 * (Prompt 77/78) now calls NotificationService.send() for real on every booking lifecycle
 * transition, so manually exercising it through this controller is no longer needed.
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

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
