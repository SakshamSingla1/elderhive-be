package com.elderhive.dtos.Review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private Long bookingId;
    private Long reviewerUserId;
    // Denormalized from User for display convenience, same precedent as BookingResponse's
    // own elderName/caretakerName.
    private String reviewerName;
    private Long caretakerId;
    private String caretakerName;
    private Integer overallRating;
    private String comment;
    private Integer punctualityRating;
    private Integer careQualityRating;
    private Integer communicationRating;
    private Long photoFileAssetId;
    // Resolved on read (FileAssetService#getById -> FileStorageService#resolveUrl), never
    // persisted - null whenever photoFileAssetId is null.
    private String photoUrl;
    // Null until the reviewed caretaker posts one (ReviewServiceImpl#upsertReply).
    private ReviewReplyResponse reply;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
