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
 * Per-user, per-type channel opt-in/out — no analog in Portfolio or sb_backend, an
 * ElderHive-original addition on top of the three-tier notification pattern those
 * reference.
 *
 * <p><b>Absence of a row for a given (userId, type) pair means "in-app only"</b> — not an
 * error, and not "no notifications at all." Every user gets the in-app bell log
 * regardless of preference (it's the lowest-friction, zero-cost channel), but email/SMS/
 * web-push all default OFF until a row exists explicitly opting in. Resolving that
 * default is the job of whichever later phase actually reads preferences at send time
 * (Phase 7's NotificationService, prompt 58) — this entity/repository only model the
 * explicit-opt-in rows themselves, the same "absence has a defined meaning, not an
 * error" contract UserThemeMapping already established for theme selection.
 *
 * <p>(userId, type) is unique — one preference row per user per notification category,
 * matching the "toggle BOOKING separately from EMERGENCY" granularity NotificationTypeEnum
 * itself is designed around.
 */
@Entity
@Table(name = "notification_preferences")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTypeEnum type;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "web_push_enabled", nullable = false)
    private boolean webPushEnabled;
}
