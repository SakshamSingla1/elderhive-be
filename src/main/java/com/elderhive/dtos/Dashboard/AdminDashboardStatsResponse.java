package com.elderhive.dtos.Dashboard;

import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Backs AdminDashboardController's single aggregate-stats endpoint (Prompt 94).
 * {@code activeUsersByRole} always has one entry per UserTypeEnum value (zero included,
 * not just the roles that happen to have an active user) - lets the admin UI render a
 * fixed set of role cards without special-casing a missing key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsResponse {

    private Map<UserTypeEnum, Long> activeUsersByRole;

    // Every booking scheduled within the current calendar week (Monday-Sunday),
    // regardless of status.
    private long bookingsThisWeek;

    // TRIGGERED or ACKNOWLEDGED, system-wide.
    private long openEmergencyAlerts;

    // The subset of openEmergencyAlerts that are also currently overdue (Prompt 84's own
    // "now - createdAt > EMERGENCY_SETTINGS.escalationTimeoutMinutes" rule) - always
    // <= openEmergencyAlerts.
    private long overdueEmergencyAlerts;

    // CaretakerProfile.verificationStatus = PENDING, awaiting an admin's approve/reject
    // decision.
    private long pendingCaretakerVerifications;
}
