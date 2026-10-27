package com.elderhive.controllers;

import com.elderhive.dtos.Payment.CreatePaymentIntentRequest;
import com.elderhive.dtos.Payment.CreatePaymentIntentResponse;
import com.elderhive.dtos.Payment.PaymentResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every ownership check lives in PaymentServiceImpl, not here - this controller just
 * resolves the caller and forwards, matching BookingController's own precedent. {@code
 * /webhook} is the one exception: it is deliberately unauthenticated (SecurityConfig
 * permits it) since Stripe itself calls it with no JWT - Stripe's signature over the raw
 * body (verified inside PaymentServiceImpl#handleWebhook) IS the auth for that one route.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/intent")
    public ResponseEntity<ResponseModel<CreatePaymentIntentResponse>> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) throws GenericException {
        return ApiResponse.createSuccess(paymentService.createIntent(request.getBookingId(), currentUser()),
                "Payment intent created successfully");
    }

    /** Raw body + signature, not a parsed DTO - Stripe's own SDK needs the exact bytes it
     *  sent to verify the Stripe-Signature header against stripe.webhook-secret. */
    @PostMapping("/webhook")
    public ResponseEntity<ResponseModel<Void>> webhook(
            @RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) throws GenericException {
        paymentService.handleWebhook(payload, signature);
        return ApiResponse.successResponse();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<PaymentResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(paymentService.getById(id, currentUser()), "Payment fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ResponseModel<List<PaymentResponse>>> getByBooking(@PathVariable Long bookingId) throws GenericException {
        return ApiResponse.successResponse(paymentService.getByBooking(bookingId, currentUser()), "Payments fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
