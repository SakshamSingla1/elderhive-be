package com.elderhive.services.impl;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.dtos.Review.ReviewReplyRequest;
import com.elderhive.dtos.Review.ReviewReplyResponse;
import com.elderhive.dtos.Review.ReviewRequest;
import com.elderhive.dtos.Review.ReviewResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.Review;
import com.elderhive.entities.ReviewReply;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.ReviewReplyRepository;
import com.elderhive.repositories.ReviewRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.CaretakerProfileService;
import com.elderhive.services.FileAssetService;
import com.elderhive.services.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final BookingRepository bookingRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final UserRepository userRepository;
    private final FileAssetService fileAssetService;
    private final CaretakerProfileService caretakerProfileService;

    @Override
    @Transactional
    public ReviewResponse create(User caller, ReviewRequest request) throws GenericException {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.BOOKING_NOT_FOUND, "Booking not found"));
        assertCanReview(booking, caller);
        if (booking.getStatus() != BookingStatusEnum.COMPLETED) {
            throw new GenericException(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION, "Only completed bookings can be reviewed");
        }
        if (reviewRepository.existsByBookingId(request.getBookingId())) {
            throw new GenericException(ExceptionCodeEnum.DUPLICATE_REVIEW, "This booking has already been reviewed");
        }

        Review review = reviewRepository.save(Review.builder()
                .bookingId(booking.getId())
                .reviewerUserId(caller.getId())
                .caretakerId(booking.getCaretakerId())
                .overallRating(request.getOverallRating())
                .comment(request.getComment())
                .punctualityRating(request.getPunctualityRating() != null ? request.getPunctualityRating() : request.getOverallRating())
                .careQualityRating(request.getCareQualityRating() != null ? request.getCareQualityRating() : request.getOverallRating())
                .communicationRating(request.getCommunicationRating() != null ? request.getCommunicationRating() : request.getOverallRating())
                .photoFileAssetId(request.getPhotoFileAssetId())
                .build());

        caretakerProfileService.recomputeRatingAverage(booking.getCaretakerId());

        return toResponse(review);
    }

    @Override
    public Page<ReviewResponse> getByCaretaker(Long caretakerId, Pageable pageable) {
        return reviewRepository.findByCaretakerId(caretakerId, pageable).map(this::toResponseUnchecked);
    }

    @Override
    public ReviewResponse getByBookingId(Long bookingId) throws GenericException {
        Review review = reviewRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.REVIEW_NOT_FOUND, "This booking has not been reviewed yet"));
        return toResponse(review);
    }

    @Override
    @Transactional
    public ReviewReplyResponse upsertReply(User caller, Long reviewId, ReviewReplyRequest request) throws GenericException {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.REVIEW_NOT_FOUND, "Review not found"));

        CaretakerProfile caretaker = caretakerProfileRepository.findById(review.getCaretakerId()).orElse(null);
        if (caretaker == null || !Objects.equals(caretaker.getUserId(), caller.getId())) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "Only the reviewed caretaker can reply to this review");
        }

        ReviewReply reply = reviewReplyRepository.findByReviewId(reviewId).orElse(null);
        if (reply == null) {
            reply = ReviewReply.builder()
                    .reviewId(reviewId)
                    .caretakerId(review.getCaretakerId())
                    .content(request.getContent())
                    .build();
        } else {
            reply.setContent(request.getContent());
        }
        reply = reviewReplyRepository.save(reply);

        return toReplyResponse(reply);
    }

    /** The booking's own family party - the original booker or a FamilyElderLink
     *  co-manager of its elder profile. Deliberately stricter than ElderSphere's own
     *  ReviewServiceImpl#create, which has no ownership check at all. */
    private void assertCanReview(Booking booking, User caller) throws GenericException {
        boolean isOwner = Objects.equals(booking.getFamilyUserId(), caller.getId());
        boolean isCoManaging = familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(booking.getElderProfileId(), caller.getId());
        if (!isOwner && !isCoManaging) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to review this booking");
        }
    }

    // Page#map's Function<T, R> signature has no checked-exception escape hatch, unlike
    // every other caller of toResponse here - same reasoning/pattern as
    // EmergencyAlertServiceImpl's own toResponseUnchecked.
    private ReviewResponse toResponseUnchecked(Review review) {
        try {
            return toResponse(review);
        } catch (GenericException e) {
            throw new RuntimeException(e.getErrorMessage(), e);
        }
    }

    private ReviewResponse toResponse(Review review) throws GenericException {
        User reviewer = userRepository.findById(review.getReviewerUserId()).orElse(null);
        CaretakerProfile caretakerProfile = caretakerProfileRepository.findById(review.getCaretakerId()).orElse(null);
        User caretakerUser = caretakerProfile != null ? userRepository.findById(caretakerProfile.getUserId()).orElse(null) : null;

        String photoUrl = null;
        if (review.getPhotoFileAssetId() != null) {
            FileAssetResponse asset = fileAssetService.getById(review.getPhotoFileAssetId());
            photoUrl = asset.getUrl();
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .bookingId(review.getBookingId())
                .reviewerUserId(review.getReviewerUserId())
                .reviewerName(reviewer != null ? reviewer.getFullName() : null)
                .caretakerId(review.getCaretakerId())
                .caretakerName(caretakerUser != null ? caretakerUser.getFullName() : null)
                .overallRating(review.getOverallRating())
                .comment(review.getComment())
                .punctualityRating(review.getPunctualityRating())
                .careQualityRating(review.getCareQualityRating())
                .communicationRating(review.getCommunicationRating())
                .photoFileAssetId(review.getPhotoFileAssetId())
                .photoUrl(photoUrl)
                .reply(reviewReplyRepository.findByReviewId(review.getId()).map(this::toReplyResponse).orElse(null))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private ReviewReplyResponse toReplyResponse(ReviewReply reply) {
        return ReviewReplyResponse.builder()
                .id(reply.getId())
                .reviewId(reply.getReviewId())
                .caretakerId(reply.getCaretakerId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}
