package com.elderhive.services;

import com.elderhive.dtos.Notification.NotificationPreferenceItemRequest;
import com.elderhive.dtos.Notification.NotificationPreferenceResponse;

import java.util.List;

/**
 * CRUD over a user's own NotificationPreference rows — deliberately separate from
 * NotificationService (Prompt 58), which only ever READS these at send time. Kept apart
 * because preferences are their own resource with their own lifecycle (a user manages them
 * from a settings page independent of any particular notification being sent).
 */
public interface NotificationPreferenceService {

    /**
     * Every NotificationTypeEnum value for userId, synthesizing the documented default
     * (in-app on, everything else off) for any type with no persisted row yet — the caller
     * never has to special-case "this type has no preference row."
     */
    List<NotificationPreferenceResponse> getPreferences(Long userId);

    /** Upserts each submitted (type, channels) row for userId, then returns the full refreshed list. */
    List<NotificationPreferenceResponse> updatePreferences(Long userId, List<NotificationPreferenceItemRequest> updates);
}
