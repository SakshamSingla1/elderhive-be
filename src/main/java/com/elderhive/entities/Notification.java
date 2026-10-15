package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.NotificationTypeEnum;
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
 * Mirrors Portfolio's Notification (profileId -> userId is the only rename) — the in-app
 * bell-icon log: one row per notification a user has actually been shown, independent of
 * whichever other channels (email/SMS/web-push) a later phase's NotificationService also
 * fires it through. Not ported: Portfolio's explicit `@JsonProperty public boolean
 * isRead()` override and `@Builder.Default private boolean isRead = false` — both are
 * redundant (Lombok already generates a correct isRead()/setRead() pair for a plain
 * `boolean isRead` field, verified directly before writing this, and `false` is already
 * boolean's natural unset default, so @Builder.Default adds nothing).
 *
 * <p>No service/controller yet — this prompt is schema-only (entity + repository +
 * migration); Phase 7's later prompts (58's NotificationService, 60's FE) are what
 * actually write/read these rows in anger.
 */
@Entity
@Table(name = "notifications")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTypeEnum type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;
}
