package com.elderhive.controllers;

import com.elderhive.dtos.NavLink.NavLinkRequest;
import com.elderhive.dtos.NavLink.NavLinkResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NavLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD endpoints are gated by NAVLINK_MANAGE via AdminPermissionGuard, not a bare
 * hasRole('ADMIN') the way ElderSphere's equivalent controller does — SUPER_ADMIN still
 * reaches them too, since the guard unconditionally grants SUPER_ADMIN regardless of
 * permission (Prompt 23).
 */
@RestController
@RequestMapping("/nav-links")
@RequiredArgsConstructor
public class NavLinkController {

    private final NavLinkService navLinkService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ResponseModel<List<NavLinkResponse>>> getMine() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(navLinkService.getMine(caller), "Navigation links fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('NAVLINK_MANAGE')")
    @PostMapping
    public ResponseEntity<ResponseModel<NavLinkResponse>> create(@Valid @RequestBody NavLinkRequest request)
            throws GenericException {
        return ApiResponse.createSuccess(navLinkService.create(request), "Navigation link created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('NAVLINK_MANAGE')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<NavLinkResponse>> update(@PathVariable Long id,
                                                                   @Valid @RequestBody NavLinkRequest request)
            throws GenericException {
        return ApiResponse.successResponse(navLinkService.update(id, request), "Navigation link updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('NAVLINK_MANAGE')")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<NavLinkResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(navLinkService.getById(id), "Navigation link fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('NAVLINK_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<NavLinkResponse>>> getAll(Pageable pageable) {
        return ApiResponse.successResponse(navLinkService.getAll(pageable), "Navigation links fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('NAVLINK_MANAGE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        navLinkService.delete(id);
        return ApiResponse.successResponse("Navigation link deleted successfully", "Navigation link deleted successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
