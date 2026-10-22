package com.elderhive.enums;

/**
 * Additive only, same rule as ExceptionCodeEnum — append a new value the first time a
 * phase needs to audit a new kind of entity (e.g. BOOKING, EMERGENCY_ALERT), never
 * rename/remove an existing one once AuditLog rows reference it.
 */
public enum EntityTypeEnum {
    USER,
    ROLE,
    EMERGENCY_ALERT
}
