package com.elderhive.webpush;

import com.elderhive.dao.push_subscription.PushSubscriptionDao;
import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationDeliveryLog;
import com.elderhive.entities.PushSubscription;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationDeliveryStatusEnum;
import com.elderhive.repositories.NotificationDeliveryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported close to verbatim from ElderSphere's WebPushSenderService — the exact fail-soft
 * control flow is preserved (a stale subscription, 404/410, is deleted and nothing is
 * logged; any other non-2xx or exception is a WARN log and nothing is logged; only a 2xx
 * response writes a delivery-log row). The only real divergence is structurally forced by
 * ElderHive's own NotificationDeliveryLog shape (built independently in Prompt 55, before
 * this prompt existed): it has {@code status}/{@code subject}/{@code body} columns rather
 * than ElderSphere's {@code simulatedRecipient}/{@code dispatchedAt}, so the success path
 * writes {@code status=SENT} and copies the notification's own title/message as the frozen
 * subject/body — there is no NotificationDeliveryLogDao in ElderHive (matching the
 * established "a DAO is only added once an entity has a prompt-driven reason for one"
 * precedent, see ColorThemeDao/UserThemeMappingDao), so the repository is used directly.
 *
 * <p>Deliberately NOT writing a FAILED-status row on any failure path, even though
 * ElderHive's enum has that value sitting unused — ElderSphere's own tests
 * (WebPushSenderServiceTest) explicitly assert {@code verify(notificationDeliveryLogDao,
 * never()).save(any())} on every failure branch, and "port close to verbatim" means
 * preserving that tested behavior rather than inventing a more complete-looking one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushSenderService {

    private final PushService pushService;
    private final PushSubscriptionDao pushSubscriptionDao;
    private final NotificationDeliveryLogRepository notificationDeliveryLogRepository;
    private final ObjectMapper objectMapper;

    public void sendToUser(Notification notification, Long userId) {
        List<PushSubscription> subscriptions = pushSubscriptionDao.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = buildPayload(notification);
        for (PushSubscription subscription : subscriptions) {
            sendOne(notification, subscription, payload);
        }
    }

    private void sendOne(Notification notification, PushSubscription subscription, String payload) {
        try {
            nl.martijndwars.webpush.Subscription webPushSubscription = new nl.martijndwars.webpush.Subscription(
                    subscription.getEndpoint(),
                    new nl.martijndwars.webpush.Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey()));
            nl.martijndwars.webpush.Notification pushNotification =
                    new nl.martijndwars.webpush.Notification(webPushSubscription, payload);

            HttpResponse response = pushService.send(pushNotification);
            int status = response.getStatusLine().getStatusCode();

            if (status == 404 || status == 410) {
                log.info("Web push subscription {} is stale (push service returned {}) - removing it",
                        subscription.getId(), status);
                pushSubscriptionDao.deleteById(subscription.getId());
                return;
            }

            if (status >= 200 && status < 300) {
                notificationDeliveryLogRepository.save(NotificationDeliveryLog.builder()
                        .notificationId(notification.getId())
                        .channel(NotificationChannelEnum.WEB_PUSH)
                        .status(NotificationDeliveryStatusEnum.SENT)
                        .subject(notification.getTitle())
                        .body(notification.getMessage())
                        .build());
                return;
            }

            log.warn("Web push to subscription {} returned unexpected status {}", subscription.getId(), status);
        } catch (Exception e) {
            log.warn("Web push send failed for subscription {} (endpoint {}): {}",
                    subscription.getId(), subscription.getEndpoint(), e.getMessage());
        }
    }

    private String buildPayload(Notification notification) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notificationId", notification.getId());
        body.put("type", notification.getType() != null ? notification.getType().name() : null);
        body.put("title", notification.getTitle());
        body.put("message", notification.getMessage());
        body.put("link", notification.getLink());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize web push payload for notification {}: {}", notification.getId(), e.getMessage());
            return "{\"title\":\"" + notification.getTitle() + "\"}";
        }
    }
}
