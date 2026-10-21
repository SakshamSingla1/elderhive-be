package com.elderhive.controllers;

import com.elderhive.dtos.ServiceOffering.ServiceOfferingRequest;
import com.elderhive.dtos.ServiceOffering.ServiceOfferingResponse;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.ServiceOfferingService;
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
 * Mutations stay admin CRUD, gated by SERVICE_MANAGE via AdminPermissionGuard - same shape
 * as ServiceCategoryController's own SERVICE_MANAGE-gated endpoints. The list and detail
 * GETs are opened to any authenticated caller (Prompt 73) to power the family-facing
 * browse view.
 */
@RestController
@RequestMapping("/api/v1/service-offerings")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ServiceOfferingService serviceOfferingService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @PostMapping
    public ResponseEntity<ResponseModel<ServiceOfferingResponse>> create(@Valid @RequestBody ServiceOfferingRequest request)
            throws GenericException {
        return ApiResponse.createSuccess(serviceOfferingService.create(request), "Service offering created successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseModel<ServiceOfferingResponse>> update(@PathVariable Long id,
                                                                           @Valid @RequestBody ServiceOfferingRequest request)
            throws GenericException {
        return ApiResponse.successResponse(serviceOfferingService.update(id, request), "Service offering updated successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<ServiceOfferingResponse>> getById(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(serviceOfferingService.getById(id), "Service offering fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ServiceOfferingResponse>>> getAll(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ServiceOfferingStatusEnum status,
            Pageable pageable) {
        return ApiResponse.successResponse(serviceOfferingService.getAll(categoryId, status, pageable), "Service offerings fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('SERVICE_MANAGE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        serviceOfferingService.delete(id);
        return ApiResponse.successResponse("Deleted", "Service offering deleted successfully");
    }
}
