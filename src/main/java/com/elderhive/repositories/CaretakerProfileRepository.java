package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaretakerProfileRepository extends JpaRepository<CaretakerProfile, Long> {

    Optional<CaretakerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // Backs AdminDashboardController's own "pending caretaker verifications" stat
    // (Prompt 94) - how many CaretakerProfiles are still awaiting an admin's
    // approve/reject decision (CaretakerVerificationDocumentController#review).
    long countByVerificationStatus(CaretakerVerificationStatusEnum verificationStatus);

    // A plain ON-clause join against User's scalar userId field (no @ManyToOne mapping
    // between these entities exists), matching ElderProfileRepository's own established
    // join pattern. Backs the Prompt 68 autocomplete search - the caller always supplies
    // a real pattern (never literally null), so there's no separate "match everything"
    // branch to worry about here.
    @Query("""
            SELECT c FROM CaretakerProfile c
            JOIN User u ON u.id = c.userId
            WHERE LOWER(u.fullName) LIKE :pattern
            ORDER BY u.fullName ASC
            """)
    List<CaretakerProfile> searchByFullNamePattern(@Param("pattern") String pattern, Pageable pageable);

    // Backs Prompt 82's emergency proximity matching: every VERIFIED caretaker with real
    // coordinates, within either their OWN serviceRadiusKm or (when they haven't set one)
    // the caller-supplied defaultRadiusKm, ordered nearest-first. The haversine formula
    // runs entirely in Postgres (no PostGIS needed for a simple great-circle distance) -
    // native SQL rather than a JPQL function since JPQL has no acos/radians support.
    //
    // least()/greatest() clamp acos's argument to [-1, 1] - without it, floating-point
    // rounding on a caretaker at (near-)identical coordinates to the alert can push the
    // raw expression a hair past 1.0, and acos() of anything outside [-1, 1] returns NaN
    // in Postgres, silently corrupting both the WHERE filter and the ORDER BY for that row.
    @Query(value = """
            SELECT * FROM caretaker_profiles c
            WHERE c.verification_status = 'VERIFIED'
              AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL
              AND (6371 * acos(least(1.0, greatest(-1.0,
                    cos(radians(:latitude)) * cos(radians(c.latitude)) * cos(radians(c.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(c.latitude))
              )))) <= COALESCE(c.service_radius_km, :defaultRadiusKm)
            ORDER BY (6371 * acos(least(1.0, greatest(-1.0,
                    cos(radians(:latitude)) * cos(radians(c.latitude)) * cos(radians(c.longitude) - radians(:longitude))
                    + sin(radians(:latitude)) * sin(radians(c.latitude))
              )))) ASC
            """, nativeQuery = true)
    List<CaretakerProfile> findVerifiedWithinRadius(@Param("latitude") double latitude,
                                                      @Param("longitude") double longitude,
                                                      @Param("defaultRadiusKm") double defaultRadiusKm);
}
