package com.elderhive.services;

import com.elderhive.dtos.Dashboard.AdminDashboardStatsResponse;
import com.elderhive.exceptions.GenericException;

public interface AdminDashboardService {

    /** DASHBOARD_VIEW-gated at the controller (held by both Admin and Super Admin, Phase
     *  3's AdminBootstrapSeeder) - a snapshot aggregate across Users, Bookings,
     *  EmergencyAlerts, and CaretakerProfiles. Every count is computed fresh on each call,
     *  no caching/background job (same "compute on read" precedent as
     *  EmergencyAlertResponse's own isOverdue field). */
    AdminDashboardStatsResponse getStats() throws GenericException;
}
