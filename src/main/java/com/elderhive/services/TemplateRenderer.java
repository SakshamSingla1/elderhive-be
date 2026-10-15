package com.elderhive.services;

import com.elderhive.exceptions.GenericException;

import java.util.Map;

/**
 * The single, mandatory path for turning a NotificationTemplate into actual notification
 * text — closes ElderSphere's gap where notification strings were hand-built inline in
 * calling code. No later phase may build a notification subject/body by string
 * concatenation; every one of them calls render() instead, keyed by the template's own
 * templateKey (never a hardcoded subject/body literal scattered across the codebase).
 */
public interface TemplateRenderer {

    /**
     * Looks up the template by templateKey, then substitutes every {{placeholder}} in its
     * subject and messageBody using variables. Throws NOTIFICATION_TEMPLATE_NOT_FOUND if
     * no such template is registered, or NOTIFICATION_TEMPLATE_PLACEHOLDER_UNRESOLVED if
     * the template references a placeholder variables has no entry for — a caller-side
     * bug (a later phase forgetting to supply a required variable) is deliberately a loud
     * failure here, not a silently-broken notification shipped with a literal
     * "{{typo}}" left in it (the exact failure mode a naive find-and-replace-over-the-
     * supplied-map-only approach would miss).
     */
    RenderedNotification render(String templateKey, Map<String, String> variables) throws GenericException;
}
