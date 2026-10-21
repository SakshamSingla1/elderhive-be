package com.elderhive.dtos.CaretakerVerificationDocument;

import com.elderhive.enums.DocumentReviewStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewDocumentRequest {

    @NotNull(message = "Review status is required")
    private DocumentReviewStatusEnum reviewStatus;
}
