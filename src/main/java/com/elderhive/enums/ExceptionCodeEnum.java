package com.elderhive.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Additive only — never renumber or rename an existing value once another phase may
 * reference it. New groups (ROLE, PERMISSION, NAVLINK, THEME, NOTIFICATION, BOOKING,
 * EMERGENCY, etc.) get appended by the phase that first needs them; within an existing
 * group, later prompts add values too (e.g. Prompt 15 adds DUPLICATE_EMAIL/PASSWORD_TOO_WEAK
 * to USER/VALIDATION when it builds registration) rather than this file front-loading
 * every code a future phase might eventually want.
 */
public enum ExceptionCodeEnum {

    // ==== COMMON / SYSTEM ERRORS ====
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR"),
    UNEXPECTED_ERROR("UNEXPECTED_ERROR"),
    DATABASE_ERROR("DATABASE_ERROR"),
    NULL_POINTER("NULL_POINTER"),
    MAPPING_ERROR("MAPPING_ERROR"),
    FORMAT_ERROR("FORMAT_ERROR"),
    // Generic 409 fallback for the CONFLICT bucket (Prompt 7's GlobalExceptionHandler) until
    // a domain-specific duplicate code exists (e.g. DUPLICATE_EMAIL lands with Prompt 15) —
    // later phases should prefer a specific code over this one once one exists for their case.
    CONFLICT("CONFLICT"),

    // ==== AUTH & SECURITY ====
    UNAUTHORIZED("UNAUTHORIZED"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS"),
    TOKEN_EXPIRED("TOKEN_EXPIRED"),
    TOKEN_INVALID("TOKEN_INVALID"),
    FORBIDDEN("FORBIDDEN"),
    // Login succeeds credential-wise but the account itself is INACTIVE/SUSPENDED (e.g.
    // Prompt 93's admin user-suspension feature) — not explicitly asked for by Prompt 16,
    // but UserStatusEnum already models this and leaving it unenforced at login would be
    // a dormant bug the moment an admin can actually suspend someone.
    ACCOUNT_NOT_ACTIVE("ACCOUNT_NOT_ACTIVE"),

    // ==== USER ====
    USER_NOT_FOUND("USER_NOT_FOUND"),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL"),
    INVALID_ROLE("INVALID_ROLE"),

    // ==== VALIDATION / INPUT ====
    BAD_REQUEST("BAD_REQUEST"),
    INVALID_ARGUMENT("INVALID_ARGUMENT"),
    VALIDATION_FAILED("VALIDATION_FAILED"),
    PASSWORD_TOO_WEAK("PASSWORD_TOO_WEAK"),
    DATA_NOT_FOUND("DATA_NOT_FOUND"),

    // ==== COLOR THEME ====
    COLOR_THEME_ALREADY_EXISTS("COLOR_THEME_ALREADY_EXISTS"),
    COLOR_THEME_NOT_FOUND("COLOR_THEME_NOT_FOUND"),

    // ==== NOTIFICATION ====
    NOTIFICATION_TEMPLATE_NOT_FOUND("NOTIFICATION_TEMPLATE_NOT_FOUND"),
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND"),
    // Deliberately NOT registered in any of GlobalExceptionHandler's four buckets — an
    // unresolved {{placeholder}} means a later phase's calling code forgot to supply a
    // variable the template requires, a programmer bug, not a user-facing 400. Falls
    // through resolveStatus's default to 500, which is the correct signal for it.
    NOTIFICATION_TEMPLATE_PLACEHOLDER_UNRESOLVED("NOTIFICATION_TEMPLATE_PLACEHOLDER_UNRESOLVED"),

    // ==== ELDER PROFILE ====
    ELDER_PROFILE_NOT_FOUND("ELDER_PROFILE_NOT_FOUND"),

    // ==== ELDER PROFILE LINK INVITE ====
    ELDER_PROFILE_LINK_INVITE_NOT_FOUND("ELDER_PROFILE_LINK_INVITE_NOT_FOUND"),

    // ==== CARETAKER PROFILE ====
    CARETAKER_PROFILE_NOT_FOUND("CARETAKER_PROFILE_NOT_FOUND"),

    // ==== FILE STORAGE ====
    FILE_NOT_FOUND("FILE_NOT_FOUND"),
    // Server-side storage failure (disk I/O error, Cloudinary API error) - not registered in
    // any GlobalExceptionHandler bucket, falls through resolveStatus's default to 500, which
    // is the correct signal for it (not a user input problem).
    FILE_STORAGE_FAILED("FILE_STORAGE_FAILED"),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED"),

    // ==== CARETAKER VERIFICATION DOCUMENT ====
    CARETAKER_VERIFICATION_DOCUMENT_NOT_FOUND("CARETAKER_VERIFICATION_DOCUMENT_NOT_FOUND"),

    // ==== SERVICE CATEGORY ====
    SERVICE_CATEGORY_ALREADY_EXISTS("SERVICE_CATEGORY_ALREADY_EXISTS"),
    SERVICE_CATEGORY_NOT_FOUND("SERVICE_CATEGORY_NOT_FOUND"),

    // ==== SERVICE OFFERING ====
    SERVICE_OFFERING_NOT_FOUND("SERVICE_OFFERING_NOT_FOUND"),

    // ==== BOOKING ====
    BOOKING_NOT_FOUND("BOOKING_NOT_FOUND"),
    // A requested status change doesn't follow BookingStatusEnum's lifecycle (e.g.
    // COMPLETED -> PENDING, or any change away from a terminal COMPLETED/CANCELLED).
    INVALID_BOOKING_STATUS_TRANSITION("INVALID_BOOKING_STATUS_TRANSITION"),
    // The requested caretaker/date/time slot is already taken by another non-terminal
    // booking - a scheduling conflict, not a missing-resource or bad-input problem.
    BOOKING_SLOT_UNAVAILABLE("BOOKING_SLOT_UNAVAILABLE"),

    // ==== EMERGENCY ALERT ====
    // A caretaker tried to claim an alert another caretaker already claimed first
    // (Prompt 84's "first claim wins" rule) - a concurrency conflict, not a missing-
    // resource or bad-input problem.
    EMERGENCY_ALERT_ALREADY_CLAIMED("EMERGENCY_ALERT_ALREADY_CLAIMED"),
    // A requested status change doesn't follow EmergencyAlertStatusEnum's lifecycle (e.g.
    // RESOLVED -> TRIGGERED, or any change away from a terminal RESOLVED).
    INVALID_EMERGENCY_ALERT_STATUS_TRANSITION("INVALID_EMERGENCY_ALERT_STATUS_TRANSITION");

    private final String value;

    private static final Map<String, ExceptionCodeEnum> valueToEnumMap = new HashMap<>();

    static {
        for (ExceptionCodeEnum code : ExceptionCodeEnum.values()) {
            valueToEnumMap.put(code.getValue().toUpperCase(), code);
        }
    }

    ExceptionCodeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ExceptionCodeEnum fromValue(String value) {
        ExceptionCodeEnum code = valueToEnumMap.get(value.toUpperCase());
        if (code != null) {
            return code;
        }
        throw new IllegalArgumentException("Invalid exception code: " + value);
    }
}
