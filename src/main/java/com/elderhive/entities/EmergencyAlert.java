package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.EmergencyAlertStatusEnum;
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

/**
 * Mirrors ~/ElderSphere/eldersphere-be's EmergencyAlert shape (lat/long capture,
 * best-effort reverse-geocoded {@code address}, status lifecycle), renamed from
 * ElderSphere's {@code resolvedAddress} to {@code address} to match this prompt's own
 * field list. No separate {@code triggeredAt}/{@code resolvedAt} columns - Auditable's own
 * {@code createdAt} IS the trigger moment (the row is created exactly when the alert
 * fires), and {@code responseTimeSeconds} (computed by the future resolve endpoint,
 * Prompt 84) is the only persisted measure of how long resolution took; {@code updatedAt}
 * approximates "last touched" for anything needing a resolved-at-ish timestamp without a
 * dedicated column. All FKs are plain scalar fields, no {@code @ManyToOne}, matching this
 * project's established cross-entity-reference convention.
 */
@Entity
@Table(name = "emergency_alerts")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlert extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_profile_id", nullable = false)
    private Long elderProfileId;

    @Column(name = "triggered_by_user_id", nullable = false)
    private Long triggeredByUserId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    // Best-effort reverse geocode of latitude/longitude via the free OpenStreetMap
    // Nominatim API (same provider ElderSphere uses) - a slow or failing geocoder must
    // never block triggering the alert itself (Prompt 83), so this is always nullable and
    // degrades to null rather than the trigger flow throwing.
    @Column(columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencyAlertStatusEnum status;

    // Nullable - set the moment a caretaker claims the alert (TRIGGERED -> ACKNOWLEDGED,
    // Prompt 84's "first claim wins" endpoint); stays null for an alert nobody has
    // responded to yet.
    @Column(name = "responding_caretaker_id")
    private Long respondingCaretakerId;

    // Nullable - computed once, at resolve time (Prompt 84), as the elapsed seconds
    // between createdAt and the moment of resolution. Never recomputed afterward.
    @Column(name = "response_time_seconds")
    private Integer responseTimeSeconds;
}
