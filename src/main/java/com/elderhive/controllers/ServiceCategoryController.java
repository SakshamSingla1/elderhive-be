package com.elderhive.controllers;

import com.elderhive.dtos.ServiceCategory.ServiceCategoryRequest;
import com.elderhive.dtos.ServiceCategory.ServiceCategoryResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.ServiceCategoryService;
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
 * Admin CRUD, gated by SERVICE_MANAGE via AdminPermissionGuard (same shape as
 * NavLinkController's own NAVLINK_MANAGE-gated endpoints) - SUPER_ADMIN still reaches
 * these too, since the guard unconditionally grants SUPER_ADMIN regardless of permission.
 */
@RestController
@RequestMapping("/api/v1/service-categories")
@RequiredArgsConstructor
public class ServiceCategoryController {

    private final ServiceCategoryService serviceCategoryService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @PostMapping
    public ResponseEntity<ResponseModel<ServiceCategoryResponse>> create(@Valid @RequestBody ServiceCategoryRequest request)
            throws GenericException {
        return ApiResponse.createSuccess(serviceCategoryService.create(request), "Service category created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<ServiceCategoryResponse>> update(@PathVariable Long id,
                                                                           @Valid @RequestBody ServiceCategoryRequest request)
            throws GenericException {
        return ApiResponse.successResponse(serviceCategoryService.update(id, request), "Service category updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<ServiceCategoryResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(serviceCategoryService.getById(id), "Service category fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ServiceCategoryResponse>>> getAll(Pageable pageable) {
        return ApiResponse.successResponse(serviceCategoryService.getAll(pageable), "Service categories fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        serviceCategoryService.delete(id);
        return ApiResponse.successResponse("Deleted", "Service category deleted successfully");
    }
}
