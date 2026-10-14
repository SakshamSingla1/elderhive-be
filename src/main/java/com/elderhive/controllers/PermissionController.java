package com.elderhive.controllers;

import com.elderhive.dtos.Permission.PermissionResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No creation endpoint, unlike ElderSphere's equivalent controller — the permission
 * catalog is seeder-owned (AdminBootstrapSeeder, Prompt 24), never created through the
 * API. Listing is read-only; assigning/unassigning a permission to a role is the only
 * mutation here, and (like RoleController) is ROLE_MANAGE-gated rather than a bare
 * hasRole('SUPER_ADMIN') — Super-Admin-only in practice today since the seeder keeps
 * ROLE_MANAGE out of the curated "Admin" role.
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<List<PermissionResponse>>> getAll() {
        return ApiResponse.successResponse(permissionService.getAll(), "Permissions fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @PostMapping("/roles/{roleId}/{permissionId}")
    public ResponseEntity<ResponseModel<String>> assign(@PathVariable Long roleId, @PathVariable Long permissionId)
            throws GenericException {
        String message = permissionService.assignToRole(roleId, permissionId);
        return ApiResponse.successResponse(message, message);
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @DeleteMapping("/roles/{roleId}/{permissionId}")
    public ResponseEntity<ResponseModel<String>> unassign(@PathVariable Long roleId, @PathVariable Long permissionId)
            throws GenericException {
        String message = permissionService.unassignFromRole(roleId, permissionId);
        return ApiResponse.successResponse(message, message);
    }
}
