package com.elderhive.dtos.CaretakerVerificationDocument;

import com.elderhive.enums.DocumentReviewStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CaretakerVerificationDocumentResponse {
    private Long id;
    private Long caretakerId;
    private Long fileAssetId;
    private String docType;
    private DocumentReviewStatusEnum reviewStatus;
    private String fileUrl;
    private String originalFilename;
    private LocalDateTime createdAt;
}
