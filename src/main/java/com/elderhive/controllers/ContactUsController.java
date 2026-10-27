package com.elderhive.controllers;

import com.elderhive.dtos.ContactUs.ContactUsRequest;
import com.elderhive.dtos.ContactUs.ContactUsResponse;
import com.elderhive.enums.ContactUsStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.ContactUsService;
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
 * {@code POST} (the public contact form submission) is the only unauthenticated route
 * here (SecurityConfig permits it); every other endpoint is CONTACT_US_MANAGE-gated.
 */
@RestController
@RequestMapping("/api/v1/contact-us")
@RequiredArgsConstructor
public class ContactUsController {

    private final ContactUsService contactUsService;

    @PostMapping
    public ResponseEntity<ResponseModel<ContactUsResponse>> create(@Valid @RequestBody ContactUsRequest request) {
        return ApiResponse.createSuccess(contactUsService.create(request), "Thanks for reaching out, we'll get back to you soon");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CONTACT_US_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ContactUsResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContactUsStatusEnum status,
            Pageable pageable) {
        return ApiResponse.successResponse(contactUsService.search(search, status, pageable), "Contact requests fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CONTACT_US_MANAGE')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseModel<ContactUsResponse>> updateStatus(
            @PathVariable Long id, @RequestParam ContactUsStatusEnum status) throws GenericException {
        return ApiResponse.successResponse(contactUsService.updateStatus(id, status), "Status updated successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CONTACT_US_MANAGE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseModel<String>> delete(@PathVariable Long id) throws GenericException {
        contactUsService.delete(id);
        return ApiResponse.successResponse("Contact request deleted successfully", "Contact request deleted successfully");
    }
}
