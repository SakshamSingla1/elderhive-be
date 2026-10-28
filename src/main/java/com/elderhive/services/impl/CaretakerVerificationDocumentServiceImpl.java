package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.CaretakerVerificationDocument;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.DocumentReviewStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.CaretakerVerificationDocumentRepository;
import com.elderhive.services.CaretakerVerificationDocumentService;
import com.elderhive.services.FileAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The folder every upload here goes into on FileStorageService is a fixed constant
 * ("caretaker-verification-documents") - this service is the ONLY caller of
 * FileAssetService#upload right now, so there's no reason to thread a caller-supplied
 * folder/resourceType through yet (that generality stays in FileStorageService itself for
 * whenever a second caller, e.g. a future profile-photo upload, actually needs it).
 */
@Service
@RequiredArgsConstructor
public class CaretakerVerificationDocumentServiceImpl implements CaretakerVerificationDocumentService {

    private static final String VERIFICATION_DOCUMENT_FOLDER = "caretaker-verification-documents";
    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final CaretakerVerificationDocumentRepository documentRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final FileAssetService fileAssetService;

    @Override
    @Transactional
    public CaretakerVerificationDocumentResponse uploadForCaller(User caller, MultipartFile file, String docType) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND,
                        "Create your caretaker profile before submitting verification documents"));

        FileAssetResponse uploaded = fileAssetService.upload(file, VERIFICATION_DOCUMENT_FOLDER, caller.getId());

        CaretakerVerificationDocument document = CaretakerVerificationDocument.builder()
                .caretakerId(profile.getId())
                .fileAssetId(uploaded.getId())
                .docType(docType)
                .reviewStatus(DocumentReviewStatusEnum.PENDING)
                .build();
        document = documentRepository.save(document);
        return toResponse(document, uploaded);
    }

    @Override
    public List<CaretakerVerificationDocumentResponse> listForCaretaker(Long caretakerId, User caller) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findById(caretakerId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));

        boolean isOwner = Objects.equals(profile.getUserId(), caller.getId());
        if (!isOwner && !ADMIN_TIER.contains(caller.getUserType())) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this caretaker's verification documents");
        }

        List<CaretakerVerificationDocumentResponse> responses = new ArrayList<>();
        for (CaretakerVerificationDocument doc : documentRepository.findByCaretakerIdOrderByCreatedAtDesc(caretakerId)) {
            responses.add(toResponse(doc, fileAssetService.getById(doc.getFileAssetId())));
        }
        return responses;
    }

    @Override
    @Transactional
    public CaretakerVerificationDocumentResponse review(Long documentId, DocumentReviewStatusEnum reviewStatus) throws GenericException {
        CaretakerVerificationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_VERIFICATION_DOCUMENT_NOT_FOUND,
                        "Verification document not found"));
        document.setReviewStatus(reviewStatus);
        document = documentRepository.save(document);

        // A reviewed document is this caretaker's one and only verification artifact today
        // (no "required document set" concept exists yet) - its review outcome IS the
        // profile's verification outcome. Without this, findVerifiedWithinRadius's
        // verification_status = 'VERIFIED' filter can never match any caretaker, since
        // nothing else in the codebase ever moves CaretakerProfile off its PENDING default.
        CaretakerProfile profile = caretakerProfileRepository.findById(document.getCaretakerId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
        profile.setVerificationStatus(switch (reviewStatus) {
            case APPROVED -> CaretakerVerificationStatusEnum.VERIFIED;
            case REJECTED -> CaretakerVerificationStatusEnum.REJECTED;
            case PENDING -> CaretakerVerificationStatusEnum.PENDING;
        });
        caretakerProfileRepository.save(profile);

        return toResponse(document, fileAssetService.getById(document.getFileAssetId()));
    }

    private CaretakerVerificationDocumentResponse toResponse(CaretakerVerificationDocument document, FileAssetResponse asset) {
        return CaretakerVerificationDocumentResponse.builder()
                .id(document.getId())
                .caretakerId(document.getCaretakerId())
                .fileAssetId(document.getFileAssetId())
                .docType(document.getDocType())
                .reviewStatus(document.getReviewStatus())
                .fileUrl(asset != null ? asset.getUrl() : null)
                .originalFilename(asset != null ? asset.getOriginalFilename() : null)
                .createdAt(document.getCreatedAt())
                .build();
    }
}
