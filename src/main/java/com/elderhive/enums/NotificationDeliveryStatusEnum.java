package com.elderhive.enums;

/**
 * The outcome of one NotificationDeliveryLog row's delivery attempt. sb_backend's own
 * NotificationV2 (the reference "outbox" table this entity mirrors) tracks this as a bare
 * Integer status code with no named values — ElderHive uses a real enum instead, matching
 * this project's own established preference (see NotificationTemplate's boolean channel
 * flags vs. the reference repos' legacy Integer 0/1 flags).
 *
 * <p>SENT covers every channel that has no real external dependency to fail yet (IN_APP is
 * just a DB write; SIMULATED_SMS/SIMULATED_EMAIL only log, per Prompt 58's
 * NotificationService) — FAILED/PENDING exist for WEB_PUSH today and for whichever
 * channels gain a real external sender later (e.g. a real SMS/email integration). Additive
 * only, same discipline as every other enum in this codebase.
 */
public enum NotificationDeliveryStatusEnum {
    PENDING,
    SENT,
    FAILED
}
