package com.elderhive.controllers;

import com.elderhive.dtos.UserTheme.UserThemeRequest;
import com.elderhive.dtos.UserTheme.UserThemeResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.UserThemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET/POST/DELETE resolve the caller's own user id from the JWT-backed SecurityContext
 * (mirroring NavLinkController's currentUser() pattern), same as Portfolio resolves its
 * own caller's profile id from the auth header via Helper.getProfileIdFromHeader.
 *
 * <p>The two reverse-lookup endpoints (by-theme list/count) are gated by THEME_MANAGE via
 * AdminPermissionGuard — a deliberate hardening vs. Portfolio, which left its equivalent
 * endpoints open to any authenticated caller. They expose every user mapped to a given
 * theme (email/full name included), which is other users' data, not something every
 * caller should be able to browse just because they're logged in.
 */
@RestController
@RequestMapping("/api/v1/user-themes")
@RequiredArgsConstructor
public class UserThemeController {

    private final UserThemeService userThemeService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<UserThemeResponse>> getTheme() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(userThemeService.getThemeByUserId(caller.getId()),
                "Active theme fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<UserThemeResponse>> setTheme(@Valid @RequestBody UserThemeRequest request)
            throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(userThemeService.setThemeForUser(caller.getId(), request.getThemeId()),
                "Theme updated successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<ResponseModel<String>> deleteTheme() throws GenericException {
        User caller = currentUser();
        String message = userThemeService.deleteThemeForUser(caller.getId());
        return ApiResponse.successResponse(message, message);
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('THEME_MANAGE')")
    @GetMapping("/theme/{themeId}")
    public ResponseEntity<ResponseModel<List<UserThemeResponse>>> getUsersByThemeId(@PathVariable Long themeId)
            throws GenericException {
        return ApiResponse.successResponse(userThemeService.getUsersByThemeId(themeId),
                "Users fetched by theme successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('THEME_MANAGE')")
    @GetMapping("/theme/{themeId}/count")
    public ResponseEntity<ResponseModel<Long>> countUsersByThemeId(@PathVariable Long themeId) {
        return ApiResponse.successResponse(userThemeService.countUsersByThemeId(themeId),
                "Theme usage count fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
