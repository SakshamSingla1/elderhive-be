package com.elderhive.enums;

// Linear lifecycle: TRIGGERED -> ACKNOWLEDGED -> RESOLVED. Unlike BookingStatusEnum there's
// no CANCELLED escape hatch - an emergency alert that turns out to be a false alarm still
// gets claimed/acknowledged and resolved through the normal flow (Prompt 84), it's simply
// resolved quickly. Enforcing which transitions are legal is a future EmergencyAlertService's
// job (INVALID_EMERGENCY_ALERT_STATUS_TRANSITION), not this enum's.
public enum EmergencyAlertStatusEnum {
    TRIGGERED,
    ACKNOWLEDGED,
    RESOLVED
}
