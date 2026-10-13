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

import java.time.LocalDateTime;

/**
 * Deliberately diverges from ElderSphere's RefreshToken (IMPLEMENTATION_PLAN.md section
 * 2): stores tokenHash (SHA-256 hex digest of the raw token), never the raw token
 * itself — a stolen database dump can't be replayed as a session. Also extends
 * Auditable, unlike ElderSphere's version, for createdBy/updatedBy/updatedAt alongside
 * its own createdAt.
 *
 * "Revoked" is a real flag here, not a delete — ElderSphere's RefreshTokenDao hard-
 * deletes rows instead, which this prompt's own field list (revoked boolean) and
 * method names (revoke/revokeAllForUser, not delete) explicitly call for instead.
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;
}
