package com.elderhive.services;

import com.elderhive.exceptions.GenericException;

import java.util.Map;

/**
 * The single, mandatory orchestration path for sending a user-facing notification — the
 * three-tier pattern's final assembly point (NotificationTemplate + TemplateRenderer from
 * Prompt 53, Notification + NotificationPreference from Prompt 54, NotificationDeliveryLog
 * from Prompt 55, Web Push from Prompt 56, the live WebSocket push from Prompt 57). No later
 * phase may hand-build a notification string or write these rows directly — every
 * user-facing notification routes through send().
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
}
