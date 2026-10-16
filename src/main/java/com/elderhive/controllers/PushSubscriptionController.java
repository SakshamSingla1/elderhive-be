package com.elderhive.controllers;

import com.elderhive.dtos.PushSubscription.PushSubscriptionRequest;
import com.elderhive.dtos.PushSubscription.PushUnsubscribeRequest;
import com.elderhive.dtos.PushSubscription.VapidPublicKeyResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.PushSubscriptionService;
import com.elderhive.webpush.VapidKeyProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flat resource path (ElderHive's own convention), not ElderSphere's nested
 * /api/v1/users/me/push-subscriptions. DELETE carries a request body (the endpoint is a
 * full URL, awkward and unsafe to place in a path segment) — the same solved design choice
 * ElderSphere itself made for this exact endpoint.
 *
 * <p>vapid-public-key lives here rather than its own controller (ElderSphere's PushController)
 * since it's a single read-only value closely tied to this same resource, not a separate
 * concern — gated isAuthenticated() rather than ElderSphere's fully public/no-auth choice:
 * ElderHive has no legitimate anonymous caller for it (the "enable push" toggle only ever
 * renders inside an already-authenticated dashboard), so there's no reason to widen
 * SecurityConfig's public surface for it.
 */
@RestController
@RequestMapping("/api/v1/push-subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final UserRepository userRepository;
    private final VapidKeyProvider vapidKeyProvider;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<ResponseModel<VapidPublicKeyResponse>> getVapidPublicKey() {
        return ApiResponse.successResponse(
                VapidPublicKeyResponse.builder().publicKey(vapidKeyProvider.getPublicKey()).build(),
                "VAPID public key fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<String>> subscribe(@Valid @RequestBody PushSubscriptionRequest request)
            throws GenericException {
        User caller = currentUser();
        pushSubscriptionService.register(caller.getId(), request);
        return ApiResponse.successResponse("Subscribed", "Push subscription registered successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<ResponseModel<String>> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request)
            throws GenericException {
        User caller = currentUser();
        pushSubscriptionService.unsubscribe(caller.getId(), request.getEndpoint());
        return ApiResponse.successResponse("Unsubscribed", "Push subscription removed successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
