package com.elderhive.controllers;

import com.elderhive.dtos.Role.RoleRequest;
import com.elderhive.dtos.Role.RoleResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.RoleService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint is gated by ROLE_MANAGE via AdminPermissionGuard, not a bare
 * hasRole('SUPER_ADMIN') the way ElderSphere's equivalent controller does — but since the
 * seeder (Prompt 24) deliberately keeps ROLE_MANAGE out of the curated "Admin" role, it's
 * Super-Admin-only in practice today anyway, just enforced through the same fine-grained
 * mechanism every other admin-gated endpoint uses rather than a special-cased check.
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @PostMapping
    public ResponseEntity<ResponseModel<RoleResponse>> create(@Valid @RequestBody RoleRequest request)
            throws GenericException {
        return ApiResponse.createSuccess(roleService.create(request), "Role created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<RoleResponse>> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request)
            throws GenericException {
        return ApiResponse.successResponse(roleService.update(id, request), "Role updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<RoleResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(roleService.getById(id), "Role fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<RoleResponse>>> getAll(Pageable pageable) {
        return ApiResponse.successResponse(roleService.getAll(pageable), "Roles fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        roleService.delete(id);
        return ApiResponse.successResponse("Role deleted successfully", "Role deleted successfully");
    }
}
