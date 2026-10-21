package com.elderhive.services;

import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.DocumentReviewStatusEnum;
import com.elderhive.exceptions.GenericException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CaretakerVerificationDocumentService {

    /** Uploads evidence against the CALLER's own caretaker profile - there's no on-behalf-of path. */
    CaretakerVerificationDocumentResponse uploadForCaller(User caller, MultipartFile file, String docType) throws GenericException;

    /** Owner (the caretaker themselves) or an ADMIN/SUPER_ADMIN may list what's been submitted. */
    List<CaretakerVerificationDocumentResponse> listForCaretaker(Long caretakerId, User caller) throws GenericException;

    /** Admin-only (CARETAKER_VERIFY-gated at the controller) - approves or rejects one submitted document. */
    CaretakerVerificationDocumentResponse review(Long documentId, DocumentReviewStatusEnum reviewStatus) throws GenericException;
}
