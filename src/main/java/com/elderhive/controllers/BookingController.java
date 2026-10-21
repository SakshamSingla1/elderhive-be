package com.elderhive.controllers;

import com.elderhive.dtos.Booking.BookingRequest;
import com.elderhive.dtos.Booking.BookingResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.BookingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every ownership/permission/transition check lives in BookingServiceImpl, not here - this
 * controller just resolves the caller and forwards. Cancelling the whole recurring series
 * a booking belongs to (vs. just that one occurrence) is a single endpoint plus a
 * {@code cancelSeries} query param (default false), not two separate routes - documented
 * here since the prompt left the choice open.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<BookingResponse>> create(@Valid @RequestBody BookingRequest request) throws GenericException {
        User caller = currentUser();
        return ApiResponse.createSuccess(bookingService.create(caller, request), "Booking created successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/confirm")
    public ResponseEntity<ResponseModel<BookingResponse>> confirm(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(bookingService.confirm(id, currentUser()), "Booking confirmed successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/start")
    public ResponseEntity<ResponseModel<BookingResponse>> start(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(bookingService.start(id, currentUser()), "Booking started successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ResponseModel<BookingResponse>> complete(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(bookingService.complete(id, currentUser()), "Booking completed successfully");
    }

    /** {@code cancelSeries=true} also cancels every other booking sharing this one's
     *  recurringGroupId (a no-op for those beyond this one if it has none). */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ResponseModel<BookingResponse>> cancel(
            @PathVariable Long id, @RequestParam(required = false, defaultValue = "false") boolean cancelSeries) throws GenericException {
        return ApiResponse.successResponse(bookingService.cancel(id, currentUser(), cancelSeries), "Booking cancelled successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<BookingResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(bookingService.getById(id, currentUser()), "Booking fetched successfully");
    }

    /** The caller's own bookings as the family member who made them. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/as-family")
    public ResponseEntity<ResponseModel<Page<BookingResponse>>> getMyBookingsAsFamily(Pageable pageable) throws GenericException {
        return ApiResponse.successResponse(bookingService.getMyBookingsAsFamily(currentUser(), pageable), "Bookings fetched successfully");
    }

    /** The caller's own bookings as the assigned caretaker. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/as-caretaker")
    public ResponseEntity<ResponseModel<Page<BookingResponse>>> getMyBookingsAsCaretaker(Pageable pageable) throws GenericException {
        return ApiResponse.successResponse(bookingService.getMyBookingsAsCaretaker(currentUser(), pageable), "Bookings fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/recurring-group/{recurringGroupId}")
    public ResponseEntity<ResponseModel<List<BookingResponse>>> getByRecurringGroupId(@PathVariable String recurringGroupId)
            throws GenericException {
        return ApiResponse.successResponse(bookingService.getByRecurringGroupId(recurringGroupId, currentUser()),
                "Recurring booking series fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
