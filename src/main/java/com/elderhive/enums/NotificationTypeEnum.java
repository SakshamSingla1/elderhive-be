package com.elderhive.enums;

/**
 * The category a Notification/NotificationPreference belongs to — deliberately coarser
 * than a NotificationTemplate's own templateKey (e.g. BOOKING_CONFIRMED/BOOKING_CANCELLED/
 * BOOKING_COMPLETED are three distinct template keys but all fall under the single
 * BOOKING type here), since per-template channel preferences would be impractical UI —
 * nobody wants to toggle three separate booking-related switches.
 *
 * <p>Additive only, same discipline as ExceptionCodeEnum — grow this as real features
 * land that need a new category, never rename/renumber an existing value. Seeded now
 * with just the categories concretely on the roadmap soonest: BOOKING (Phase 10),
 * EMERGENCY (Phase 11), ACCOUNT (security/OTP-style notifications, e.g. a later phase's
 * OTP_CODE), and SYSTEM (a generic catch-all for anything else, mirroring Portfolio's own
 * WELCOME-style generic notifications).
 */
public enum NotificationTypeEnum {
    BOOKING,
    EMERGENCY,
    ACCOUNT,
    SYSTEM
}
