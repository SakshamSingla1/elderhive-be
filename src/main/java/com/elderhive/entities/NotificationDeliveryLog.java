package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationDeliveryStatusEnum;
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
 * The outbox/delivery record this project's three-tier notification pattern ends with —
 * mirrors sb_backend's NotificationV2 in spirit (a per-attempt delivery record with a
 * frozen rendered-content copy and a status), not field-for-field: NotificationV2 carries
 * a pile of per-channel send metadata (emailTo/emailCc/whatsappTemplateName/
 * whatsappCampaignName/attachments/dltTemplateId/...) that belongs to an actual external
 * send integration ElderHive doesn't have yet (Phase 7's SIMULATED_SMS/SIMULATED_EMAIL
 * channels just log, per the plan's own Prompt 58), and tracks ONE status for the whole
 * row rather than NotificationV2's separate status/emailStatus/whatsappErrorLog fields —
 * ElderHive instead writes one NotificationDeliveryLog row PER (Notification, channel)
 * pair, so each channel's own outcome already has its own row and its own status column,
 * with no need to fan them out across parallel per-channel fields on a single row.
 *
 * <p><b>subject/body are a FROZEN copy</b> of whatever TemplateRenderer actually produced
 * at send time (Prompt 53's RenderedNotification) — deliberately duplicated here rather
 * than re-rendered on read, so that editing the source NotificationTemplate later can
 * never retroactively change what this log says was actually sent. This is the
 * project's own permanent delivery history, not a live view of the current template.
 *
 * <p>No service/controller yet — this prompt is schema-only (entity + repository +
 * migration); Phase 7's NotificationService (Prompt 58) is what actually writes these
 * rows.
 */
@Entity
@Table(name = "notification_delivery_logs")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeliveryLog extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannelEnum channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationDeliveryStatusEnum status;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;
}
