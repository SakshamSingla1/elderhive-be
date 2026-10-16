package com.elderhive.services;

import com.elderhive.dtos.Notification.NotificationResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * The single, mandatory orchestration path for sending a user-facing notification — the
 * three-tier pattern's final assembly point (NotificationTemplate + TemplateRenderer from
 * Prompt 53, Notification + NotificationPreference from Prompt 54, NotificationDeliveryLog
 * from Prompt 55, Web Push from Prompt 56, the live WebSocket push from Prompt 57). No later
 * phase may hand-build a notification string or write these rows directly — every
 * user-facing notification routes through send().
 *
 * <p>Also owns reading back and marking read a user's own in-app inbox (Prompt 60) — these
 * stayed on this service rather than a separate one since they operate on the exact same
 * Notification rows send() writes. Preference CRUD is deliberately a separate
 * NotificationPreferenceService: preferences are their own resource with their own lifecycle,
 * not something a single send() call reads and writes.
 */
public interface NotificationService {

    /**
     * Renders templateKey with variables (Prompt 53's TemplateRenderer), writes the in-app
     * Notification row (always) plus a NotificationDeliveryLog row for every channel that is
     * both applicable (the template's own isInApp/isEmail/isSms/isWebPush flags) and enabled
     * (targetUserId's NotificationPreference for the template's type — absent preference
     * means email/SMS/web-push are all off, matching NotificationPreference's own documented
     * default), and broadcasts the saved Notification over
     * {@code /topic/notifications/targetUserId}.
     */
    void send(String templateKey, Map<String, String> variables, Long targetUserId) throws GenericException;

    /** The caller's own notifications, newest first. */
    Page<NotificationResponse> getByUser(Long userId, Pageable pageable);

    /** The caller's own unread count — what NotificationBell's 30s poll fallback reads. */
    long getUnreadCount(Long userId);

    /**
     * Marks one notification read. Throws NOTIFICATION_NOT_FOUND both when the id doesn't
     * exist AND when it exists but belongs to a different user — never distinguishing the two
     * to a caller, so one user can't probe for another's notification ids via the error
     * response.
     */
    void markAsRead(Long notificationId, Long userId) throws GenericException;

    /** Marks every currently-unread notification for userId as read. A no-op if none are unread. */
    void markAllAsRead(Long userId);
}
