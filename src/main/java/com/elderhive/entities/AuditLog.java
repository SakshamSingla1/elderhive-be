package com.elderhive.entities;

import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
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
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * The compliance audit trail itself — deliberately does NOT extend Auditable (that's for
 * domain entities whose own createdAt/updatedAt/createdBy/updatedBy get tracked; this
 * table instead records "entity X of type Y had action Z done to it by user W at time T,
 * here's a JSON snapshot of what changed"). Polymorphic: one table for every auditable
 * entity type, distinguished by entityType + entityId.
 *
 * Shape mirrors ~/SakshamSingla/sb_backend's audit_logs_v2 (com.core.avighna.entities.AuditLog),
 * adapted for Postgres and this project's own EntityTypeEnum/ActionType values.
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityId;

    @Enumerated(EnumType.STRING)
    private EntityTypeEnum entityType;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private Long createdBy;

    @CreationTimestamp
    private Instant timestamp;

    @Column(columnDefinition = "TEXT")
    private String data;
}
