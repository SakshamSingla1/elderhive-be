package com.elderhive.services;

/**
 * TemplateRenderer's output — the fully-substituted subject/body for one render, channel-
 * agnostic (a later phase's delivery service decides which channels to actually send
 * through, consulting the template's own isInApp/isEmail/isSms/isWebPush flags).
 */
public record RenderedNotification(String subject, String body) {
}
