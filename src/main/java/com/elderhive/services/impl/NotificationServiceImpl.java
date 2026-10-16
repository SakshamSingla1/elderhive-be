package com.elderhive.services.impl;

import com.elderhive.dtos.Notification.NotificationResponse;
import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationDeliveryLog;
import com.elderhive.entities.NotificationPreference;
import com.elderhive.entities.NotificationTemplate;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationDeliveryStatusEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationDeliveryLogRepository;
import com.elderhive.repositories.NotificationPreferenceRepository;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.NotificationService;
import com.elderhive.services.RenderedNotification;
import com.elderhive.services.TemplateRenderer;
import com.elderhive.webpush.WebPushSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Ties together every piece Prompts 53-57 built. Deliberately does its OWN
 * NotificationTemplateRepository read (in addition to calling TemplateRenderer, which does
 * its own internal lookup by the same templateKey) — TemplateRenderer's RenderedNotification
 * is intentionally channel-agnostic (see its own javadoc: "a later phase's delivery service
 * decides which channels to actually send through, consulting the template's own
 * isInApp/isEmail/isSms/isWebPush flags"), so this is exactly that later phase, and needs
 * the entity itself, not just its rendered text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final TemplateRenderer templateRenderer;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationDeliveryLogRepository notificationDeliveryLogRepository;
    private final WebPushSenderService webPushSenderService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void send(String templateKey, Map<String, String> variables, Long targetUserId) throws GenericException {
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND,
                        "User not found: " + targetUserId));

        RenderedNotification rendered = templateRenderer.render(templateKey, variables);

        NotificationTemplate template = notificationTemplateRepository.findByTemplateKey(templateKey)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND,
                        "Notification template not found: " + templateKey));
        NotificationTypeEnum type = template.getType();

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(targetUserId)
                .type(type)
                .title(rendered.subject())
                .message(rendered.body())
                .isRead(false)
                .build());

        // IN_APP is unconditional - every user gets the bell-icon log regardless of the
        // template's own channel flags or the user's preference (see NotificationPreference's
        // own "absence means in-app only" contract: this is the one channel that is never
        // gated at all).
        writeDeliveryLog(notification, NotificationChannelEnum.IN_APP, rendered);

        NotificationPreference preference = notificationPreferenceRepository
                .findByUserIdAndType(targetUserId, type)
                .orElse(null);
        boolean smsEnabled = preference != null && preference.isSmsEnabled();
        boolean emailEnabled = preference != null && preference.isEmailEnabled();
        boolean webPushEnabled = preference != null && preference.isWebPushEnabled();

        if (template.isSms() && smsEnabled) {
            log.info("Simulated SMS to user {}: {}", targetUserId, rendered.subject());
            writeDeliveryLog(notification, NotificationChannelEnum.SIMULATED_SMS, rendered);
        }

        if (template.isEmail() && emailEnabled) {
            log.info("Simulated email to user {}: {}", targetUserId, rendered.subject());
            writeDeliveryLog(notification, NotificationChannelEnum.SIMULATED_EMAIL, rendered);
        }

        if (template.isWebPush() && webPushEnabled) {
            // WebPushSenderService writes its own NotificationDeliveryLog row(s) on success
            // and no-ops (no row, no exception) when the user has no active subscription or
            // every send attempt fails - exactly "if the user has an active subscription"
            // without this method needing its own separate existence check.
            webPushSenderService.sendToUser(notification, targetUserId);
        }

        // Broadcasts the mapped DTO, not the raw entity - matches every other controller's
        // own "never serialize an entity directly" convention, and (not just cosmetic)
        // NotificationResponse.isRead carries the @JsonProperty pin its own javadoc explains;
        // the bare entity's isRead getter would otherwise serialize as "read", which
        // NotificationBell.tsx's live-push handler silently reads as always-undefined.
        messagingTemplate.convertAndSend("/topic/notifications/" + targetUserId, mapToResponse(notification));
    }

    @Override
    public Page<NotificationResponse> getByUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) throws GenericException {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.NOTIFICATION_NOT_FOUND,
                        "Notification not found: " + notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .link(notification.getLink())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private void writeDeliveryLog(Notification notification, NotificationChannelEnum channel,
                                   RenderedNotification rendered) {
        notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                .notificationId(notification.getId())
                .channel(channel)
                .status(NotificationDeliveryStatusEnum.SENT)
                .subject(rendered.subject())
                .body(rendered.body())
                .build());
    }
}
