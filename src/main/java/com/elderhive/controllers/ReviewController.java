package com.elderhive.controllers;

import com.elderhive.dtos.Review.ReviewReplyRequest;
import com.elderhive.dtos.Review.ReviewReplyResponse;
import com.elderhive.dtos.Review.ReviewRequest;
import com.elderhive.dtos.Review.ReviewResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.ReviewService;
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

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    // FAMILY_MEMBER-only at the role level - ReviewServiceImpl#assertCanReview still
    // checks that THIS specific caller actually owns (or co-manages) THIS specific
    // booking's elder profile, same split EmergencyAlertController's own trigger() uses.
    @PreAuthorize("hasRole('FAMILY_MEMBER')")
    @PostMapping
    public ResponseEntity<ResponseModel<ReviewResponse>> create(@Valid @RequestBody ReviewRequest request) throws GenericException {
        return ApiResponse.createSuccess(reviewService.create(currentUser(), request), "Review submitted successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/caretaker/{caretakerId}")
    public ResponseEntity<ResponseModel<Page<ReviewResponse>>> getByCaretaker(@PathVariable Long caretakerId, Pageable pageable) {
        return ApiResponse.successResponse(reviewService.getByCaretaker(caretakerId, pageable), "Reviews fetched successfully");
    }

    // 404 (REVIEW_NOT_FOUND) means "not reviewed yet", not a real error - same "404 is a
    // normal answer" contract ElderProfileController's own getMyProfile() established.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ResponseModel<ReviewResponse>> getByBookingId(@PathVariable Long bookingId) throws GenericException {
        return ApiResponse.successResponse(reviewService.getByBookingId(bookingId), "Review fetched successfully");
    }

    @PreAuthorize("hasRole('CARETAKER')")
    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<ResponseModel<ReviewReplyResponse>> upsertReply(
            @PathVariable Long reviewId, @Valid @RequestBody ReviewReplyRequest request) throws GenericException {
        return ApiResponse.successResponse(reviewService.upsertReply(currentUser(), reviewId, request), "Reply saved successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
