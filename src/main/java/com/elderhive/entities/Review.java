package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Mirrors ~/ElderSphere/eldersphere-be's Review field-for-field, with two deliberate
 * renames to match ElderHive's own conventions: {@code reviewerId} -> {@code
 * reviewerUserId} (this project's established "...UserId" suffix for an actor field
 * referencing the users table, e.g. EmergencyAlert.triggeredByUserId) and {@code rating}
 * -> {@code overallRating} (self-documenting against the three named sub-ratings below,
 * and the exact field name a later analytics prompt already expects - see
 * IMPLEMENTATION_PLAN.md's own "Review.overallRating" reference). All FKs are plain
 * scalar fields, no {@code @ManyToOne}, matching this project's established cross-entity-
 * reference convention.
 */
@Entity
@Table(name = "reviews")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique - one review per booking (Prompt 88's own documented rule), enforced both
    // here (DB UNIQUE constraint) and in ReviewServiceImpl (an explicit existsByBookingId
    // check first, for a clean DUPLICATE_REVIEW error instead of a raw constraint-
    // violation exception surfacing).
    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    // Denormalized from the booking at review-creation time (same precedent as
    // EmergencyAlert's own denormalized fields) - lets review listings/the rating-average
    // recompute query both go straight at Review by caretakerId without a join back to
    // Booking for every row.
    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // Optional rating breakdown - ReviewServiceImpl defaults each to overallRating when
    // the caller omits it (same precedent ElderSphere's own ReviewServiceImpl applies).
    @Column(name = "punctuality_rating")
    private Integer punctualityRating;

    @Column(name = "care_quality_rating")
    private Integer careQualityRating;

    @Column(name = "communication_rating")
    private Integer communicationRating;

    // Optional - a previously-uploaded FileAsset (via the existing generic upload
    // endpoint), the same "accept a pre-existing fileAssetId, don't handle the upload
    // itself here" pattern CaretakerVerificationDocument already established.
    @Column(name = "photo_file_asset_id")
    private Long photoFileAssetId;
}
