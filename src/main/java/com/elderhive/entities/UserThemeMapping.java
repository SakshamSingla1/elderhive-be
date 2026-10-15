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
 * Mirrors Portfolio's ProfileThemeMapping exactly, renamed for ElderHive's domain (users,
 * not portfolio profiles) — one row per user tracking their currently-active ColorTheme.
 * Absence of a row for a given userId means "using the default theme" (resolved by
 * Prompt 44's UserThemeService), not an error condition — this table is a reverse-lookup
 * override, not a mandatory assignment.
 */
@Entity
@Table(name = "user_theme_mappings")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserThemeMapping extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;
}
