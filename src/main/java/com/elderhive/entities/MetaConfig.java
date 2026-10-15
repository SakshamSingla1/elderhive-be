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
 * A generic admin-editable settings row: {@code context} names the setting (e.g.
 * "ANNOUNCEMENT_BANNER"), {@code data} is an opaque JSON string the backend never parses
 * — the admin UI and whatever eventually reads a given context own its shape between
 * them. Mirrors the DSA tracker's MetaConfig directly, but extends Auditable (like every
 * other ElderHive entity) instead of hand-rolling updatedAt/updatedBy fields set manually
 * in the service — JPA auditing (JpaAuditingConfig + AuditorAwareImpl) populates them the
 * same way it does for every other entity, and gets createdAt/createdBy for free too.
 *
 * <p>Deliberately NOT {@code @Lob} on `data`, unlike the DSA tracker's version — Hibernate
 * 6 maps a {@code @Lob String} to Postgres' large-object {@code oid} type by default,
 * which doesn't match a plain {@code TEXT} column (confirmed the hard way: ddl-auto=
 * validate fails to boot against this project's own V8 migration with exactly that
 * mismatch). Same {@code columnDefinition = "TEXT"} convention already used by
 * AuditLog/Permission/Role's own large text columns.
 */
@Entity
@Table(name = "meta_config")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaConfig extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String context;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String data;
}
