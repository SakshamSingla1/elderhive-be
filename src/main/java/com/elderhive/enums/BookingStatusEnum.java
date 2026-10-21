package com.elderhive.enums;

// Linear lifecycle: PENDING -> CONFIRMED -> IN_PROGRESS -> COMPLETED, with CANCELLED
// reachable from any of the first three. Enforcing which transitions are actually legal is
// a future BookingService's job (INVALID_BOOKING_STATUS_TRANSITION), not this enum's.
public enum BookingStatusEnum {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
