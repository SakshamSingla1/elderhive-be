package com.elderhive.services;

import com.elderhive.dtos.Review.ReviewReplyRequest;
import com.elderhive.dtos.Review.ReviewReplyResponse;
import com.elderhive.dtos.Review.ReviewRequest;
import com.elderhive.dtos.Review.ReviewResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    /**
     * Only postable once the referenced Booking is COMPLETED, and only by that booking's
     * own family party (the original booker or a FamilyElderLink co-manager of its elder
     * profile - same "family party" concept BookingServiceImpl already uses elsewhere,
     * not just the literal person who clicked "book") - unlike ElderSphere's own
     * ReviewServiceImpl#create, which has no ownership check at all and lets any
     * authenticated caller review any booking by id. One review per booking
     * (DUPLICATE_REVIEW if one already exists). Recomputes the reviewed caretaker's
     * CaretakerProfile.ratingAverage via CaretakerProfileService afterward.
     */
    ReviewResponse create(User caller, ReviewRequest request) throws GenericException;

    /** Every review a caretaker has received, newest first by default (caller's Pageable). */
    Page<ReviewResponse> getByCaretaker(Long caretakerId, Pageable pageable);

    /** The review for one specific booking, if it's been reviewed yet - REVIEW_NOT_FOUND
     *  otherwise (the same "404 means nothing yet" contract ElderProfileController's own
     *  getMyProfile() already established, not a real error). */
    ReviewResponse getByBookingId(Long bookingId) throws GenericException;

    /**
     * Creates or updates (upsert) the caller's own reply to a review - one reply per
     * review, enforced by updating in place rather than ever inserting a second row. Only
     * the reviewed caretaker (review.caretakerId's own CaretakerProfile) may call this.
     */
    ReviewReplyResponse upsertReply(User caller, Long reviewId, ReviewReplyRequest request) throws GenericException;
}
