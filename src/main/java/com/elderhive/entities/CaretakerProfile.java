package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Deliberately diverges from ElderSphere's own CaretakerProfile per IMPLEMENTATION_PLAN.md
 * section 2, point 2: real {@code latitude}/{@code longitude}/{@code serviceRadiusKm} replace
 * ElderSphere's free-text {@code serviceArea} — Phase 11's proximity matching (emergency
 * fan-out) needs real coordinates to run a haversine query, not a string to pattern-match.
 *
 * <p>{@code specialties} is now a real many-to-many to {@link ServiceCategory} (Prompt 72),
 * reusing the same {@code caretaker_profile_specialties} join table Prompt 64 named for
 * exactly this conversion - it was a plain string {@code @ElementCollection} placeholder
 * until this prompt, since there was no ServiceCategory table yet to point at.
 */
@Entity
@Table(name = "caretaker_profiles")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "caretaker_profile_specialties",
            joinColumns = @JoinColumn(name = "caretaker_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "service_category_id")
    )
    private Set<ServiceCategory> specialties = new HashSet<>();

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    /** Denormalized - recomputed from Review rows once Phase 12 lands, never set directly by a caretaker. */
    @Column(name = "rating_average")
    private Double ratingAverage;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private CaretakerVerificationStatusEnum verificationStatus;

    @Column(name = "profile_photo_file_asset_id")
    private Long profilePhotoFileAssetId;

    private Double latitude;

    private Double longitude;

    @Column(name = "service_radius_km")
    private Double serviceRadiusKm;
}
