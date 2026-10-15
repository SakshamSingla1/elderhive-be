package com.elderhive.controllers;

import com.elderhive.dtos.ColorTheme.ColorThemeRequest;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.ColorThemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The theme catalog is a platform-level asset, same as Portfolio treats it — mutations
 * are gated by a direct hasRole('SUPER_ADMIN') check, NOT the Phase 3 Role/Permission
 * system (@adminPermissionGuard), because a color theme isn't a per-tenant admin
 * capability like role/permission/config management is. Reads are open to any
 * authenticated user since every role (SUPER_ADMIN/ADMIN/ELDER/FAMILY_MEMBER/CARETAKER)
 * needs to browse the catalog and apply a theme to its own dashboard (see Prompt 44's
 * UserThemeMapping).
 */
@RestController
@RequestMapping("/api/v1/color-themes")
@RequiredArgsConstructor
public class ColorThemeController {

    private final ColorThemeService colorThemeService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ResponseModel<ColorThemeResponse>> createTheme(@Valid @RequestBody ColorThemeRequest request)
            throws GenericException {
        return ApiResponse.createSuccess(colorThemeService.createTheme(request), "Color theme created successfully");
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<ColorThemeResponse>> updateTheme(@PathVariable Long id,
                                                                          @Valid @RequestBody ColorThemeRequest request)
            throws GenericException {
        return ApiResponse.successResponse(colorThemeService.updateTheme(id, request), "Color theme updated successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<ColorThemeResponse>> getThemeById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(colorThemeService.getThemeById(id), "Color theme fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ColorThemeResponse>>> getAllThemes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ColorThemeStatusEnum status,
            Pageable pageable) {
        return ApiResponse.successResponse(colorThemeService.getAllThemes(search, status, pageable),
                "Color themes fetched successfully");
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> deleteTheme(@PathVariable Long id) throws GenericException {
        String message = colorThemeService.deleteTheme(id);
        return ApiResponse.successResponse(message, message);
    }
}
