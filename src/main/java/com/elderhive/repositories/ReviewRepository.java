package com.elderhive.repositories;

import com.elderhive.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    Optional<Review> findByBookingId(Long bookingId);

    Page<Review> findByCaretakerId(Long caretakerId, Pageable pageable);

    // Backs CaretakerProfileServiceImpl#recomputeRatingAverage - null (not 0) when the
    // caretaker has no reviews at all yet, so the caller can tell "no reviews" apart from
    // "reviews exist and average to 0" (overallRating is 1-5, so a real 0 average can
    // never happen, but null is still the more honest "nothing to average" signal).
    @Query("SELECT AVG(r.overallRating) FROM Review r WHERE r.caretakerId = :caretakerId")
    Double findAverageOverallRatingByCaretakerId(@Param("caretakerId") Long caretakerId);
}
