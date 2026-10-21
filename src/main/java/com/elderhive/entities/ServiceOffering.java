package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.ServiceOfferingStatusEnum;
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

/**
 * A bookable service (Phase 10's eventual Booking entity references these), scoped under
 * an admin-managed ServiceCategory (Prompt 71) - {@code categoryId} is a plain scalar FK
 * field, no {@code @ManyToOne} mapping, matching this project's established convention for
 * cross-entity references (ElderProfile.familyUserId, FamilyElderLink.elderProfileId, ...).
 */
@Entity
@Table(name = "service_offerings")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOffering extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ServiceOfferingStatusEnum status = ServiceOfferingStatusEnum.ACTIVE;
}
