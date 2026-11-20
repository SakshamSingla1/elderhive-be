package com.elderhive.controllers;

import com.elderhive.calendar.CaretakerCalendarService;
import com.elderhive.dtos.CaretakerCalendar.CalendarLinkResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /me/calendar.ics is deliberately PUBLIC (SecurityConfig permitAll) despite the "me"
 * in its path - calendar apps fetch it unauthenticated-by-cookie on their own schedule, so
 * the unguessable `token` query param IS the authorization, not the caller's own session.
 * Both other routes stay behind the normal authenticated()+currentUser() gate, same as
 * every other "me" endpoint in this codebase.
 */
@RestController
@RequestMapping("/api/v1/caretakers")
@RequiredArgsConstructor
public class CaretakerCalendarController {

    private final CaretakerCalendarService caretakerCalendarService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/calendar-link")
    public ResponseEntity<ResponseModel<CalendarLinkResponse>> getCalendarLink() throws GenericException {
        String icsUrl = caretakerCalendarService.getOrCreateCalendarLink(currentUser().getId());
        return ApiResponse.successResponse(new CalendarLinkResponse(icsUrl));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/calendar-link/regenerate")
    public ResponseEntity<ResponseModel<CalendarLinkResponse>> regenerateCalendarLink() throws GenericException {
        String icsUrl = caretakerCalendarService.regenerateCalendarLink(currentUser().getId());
        return ApiResponse.successResponse(new CalendarLinkResponse(icsUrl), "Calendar link regenerated - the old link no longer works");
    }

    @GetMapping(value = "/me/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getCalendarFeed(@RequestParam String token) throws GenericException {
        String ics = caretakerCalendarService.buildIcsFeed(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"caretaker-calendar.ics\"")
                .body(ics);
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
