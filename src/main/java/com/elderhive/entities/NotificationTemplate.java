package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.NotificationTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Mirrors Portfolio's/sb_backend's NotificationTemplate in spirit (same three-tier
 * pattern: this + NotificationTemplateVariables + a later phase's delivery-log entity,
 * matching sb_backend's NotificationV2 shape), not field-for-field — those two reference
 * versions carry a pile of channel-specific delivery fields (emailTo/emailCc/emailBcc/
 * whatsappTemplateName/whatsappTemplateBody/dltTemplateId/templateGroupId/...) that belong
 * to an actual SEND attempt, not a reusable template; ElderHive keeps this entity to just
 * what a template actually is — content plus which channels it's eligible for — and
 * leaves per-send delivery details to whatever later phase builds that delivery-log
 * entity. isSms/isEmail are booleans here, not the reference repos' legacy Integer 0/1
 * flags, and isWhatsapp is dropped in favor of isInApp/isWebPush — ElderHive has no
 * WhatsApp channel, but does have in-app and web-push notifications.
 *
 * <p>No column enforces well-formed {{placeholder}} syntax in subject/messageBody — that's
 * TemplateRenderer's job at render time (Prompt 52), including catching a template that
 * references a placeholder no caller ever supplies a value for.
 *
 * <p><b>{@code type} (added Prompt 58)</b> is the NotificationTypeEnum category
 * NotificationService consults against a target user's NotificationPreference row —
 * deliberately a real column set when the template is created, not inferred by parsing the
 * templateKey string (e.g. a naive "prefix before the first underscore" convention happens
 * to work for BOOKING_CONFIRMED/EMERGENCY_ALERT_FAMILY but silently breaks for a key like
 * OTP_CODE, which is ACCOUNT-typed but has no "OTP" enum value to parse out). A later admin
 * CRUD surface over this entity (Prompt 237) lists subject/body/channel-flags as the
 * editable fields — type is categorization fixed at creation, not something that gets
 * casually re-edited alongside copy changes.
 */
@Entity
@Table(name = "notification_templates")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", unique = true, nullable = false)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTypeEnum type;

    private String subject;

    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "is_in_app", nullable = false)
    private boolean isInApp;

    @Column(name = "is_email", nullable = false)
    private boolean isEmail;

    @Column(name = "is_sms", nullable = false)
    private boolean isSms;

    @Column(name = "is_web_push", nullable = false)
    private boolean isWebPush;
}
