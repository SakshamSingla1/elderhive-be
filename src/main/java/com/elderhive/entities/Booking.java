package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.BookingStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Mirrors ElderSphere's Booking field-for-field, with two deliberate renames to match
 * ElderHive's own naming: {@code serviceId} -> {@code serviceOfferingId} (ElderHive's
 * catalog entity is ServiceOffering, not Service) and the recurring-group column folded
 * into this same migration rather than ElderSphere's later-added-on ALTER TABLE. All FKs
 * are plain scalar fields, no {@code @ManyToOne}, matching this project's established
 * cross-entity-reference convention.
 */
@Entity
@Table(name = "bookings")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_user_id", nullable = false)
    private Long familyUserId;

    @Column(name = "elder_profile_id", nullable = false)
    private Long elderProfileId;

    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;

    @Column(name = "service_offering_id", nullable = false)
    private Long serviceOfferingId;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatusEnum status;

    private BigDecimal cost;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Nullable - only present when this booking was created as part of a recurring
    // series, letting all of that series' rows be listed/cancelled together. A plain
    // String (not java.util.UUID) so it round-trips through JSON/JPA exactly like every
    // other scalar field here, matching ElderSphere's own choice.
    @Column(name = "recurring_group_id", length = 36)
    private String recurringGroupId;
}
