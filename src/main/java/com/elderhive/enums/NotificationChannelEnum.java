package com.elderhive.enums;

/**
 * Which channel a single NotificationDeliveryLog row represents one attempt through — one
 * row per (Notification, channel) pair, not one row per Notification. SIMULATED_SMS/
 * SIMULATED_EMAIL are deliberately distinct from a future real SMS/email channel: a later
 * phase wiring up a real sender (e.g. Twilio) adds a new value here rather than reusing
 * these, so delivery history always honestly reflects which path actually ran for a given
 * row, even after the simulated and real paths coexist behind a feature flag/config check.
 *
 * <p>Additive only, same discipline as every other enum in this codebase.
 */
public enum NotificationChannelEnum {
    IN_APP,
    SIMULATED_SMS,
    SIMULATED_EMAIL,
    WEB_PUSH
}
