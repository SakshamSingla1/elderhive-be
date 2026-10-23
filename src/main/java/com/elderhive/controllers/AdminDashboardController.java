package com.elderhive.controllers;

import com.elderhive.dtos.Dashboard.AdminDashboardStatsResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors ~/DSA_Java/backend's own AdminDashboardController shape (one controller per
 * admin concern, a single aggregate-stats endpoint) - same flat {@code /api/v1/<resource>}
 * URL convention every other admin-gated endpoint in this codebase already uses (no
 * separate "/admin" path segment anywhere; access is controlled purely by
 * {@code @PreAuthorize}, not URL namespace).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('DASHBOARD_VIEW')")
    @GetMapping("/stats")
    public ResponseEntity<ResponseModel<AdminDashboardStatsResponse>> stats() throws GenericException {
        return ApiResponse.successResponse(adminDashboardService.getStats(), "Dashboard stats fetched successfully");
    }
}
