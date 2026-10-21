package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.ServiceCategoryStatusEnum;
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
 * Deliberately diverges from ElderSphere's hardcoded ServiceOffering.category Java enum
 * (IMPLEMENTATION_PLAN.md section 2, point 3) - this is admin-manageable DATA, so a new
 * category ("temple visit", "grocery shopping") is a pure admin CRUD action, never a code
 * change or redeploy. The starter set (Prompt 71's own migration) is seed data in this
 * table, not enum values.
 */
@Entity
@Table(name = "service_categories")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCategory extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ServiceCategoryStatusEnum status = ServiceCategoryStatusEnum.ACTIVE;
}
