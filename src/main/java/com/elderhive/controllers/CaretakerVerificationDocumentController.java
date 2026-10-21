package com.elderhive.controllers;

import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.dtos.CaretakerVerificationDocument.ReviewDocumentRequest;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.CaretakerVerificationDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/caretaker-verification-documents")
@RequiredArgsConstructor
public class CaretakerVerificationDocumentController {

    private final CaretakerVerificationDocumentService caretakerVerificationDocumentService;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('CARETAKER_VERIFY')")
    @PutMapping("/{id}/review")
    public ResponseEntity<ResponseModel<CaretakerVerificationDocumentResponse>> review(
            @PathVariable Long id, @Valid @RequestBody ReviewDocumentRequest request) throws GenericException {
        return ApiResponse.successResponse(caretakerVerificationDocumentService.review(id, request.getReviewStatus()),
                "Verification document review recorded successfully");
    }
}
