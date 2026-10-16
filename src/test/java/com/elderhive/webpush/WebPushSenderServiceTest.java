package com.elderhive.webpush;

import com.elderhive.dao.push_subscription.PushSubscriptionDao;
import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationDeliveryLog;
import com.elderhive.entities.PushSubscription;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationDeliveryStatusEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.repositories.NotificationDeliveryLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ported close to verbatim from ElderSphere's WebPushSenderServiceTest — same fail-soft
 * assertions on every non-2xx/exception branch, adapted only for ElderHive's
 * NotificationDeliveryLogRepository (no DAO) and status/subject/body columns instead of
 * simulatedRecipient/dispatchedAt.
 */
class WebPushSenderServiceTest {

    private static final Long USER_ID = 10L;

    private PushService pushService;
    private PushSubscriptionDao pushSubscriptionDao;
    private NotificationDeliveryLogRepository notificationDeliveryLogRepository;
    private WebPushSenderService webPushSenderService;

    @BeforeEach
    void setUp() {
        pushService = mock(PushService.class);
        pushSubscriptionDao = mock(PushSubscriptionDao.class);
        notificationDeliveryLogRepository = mock(NotificationDeliveryLogRepository.class);
        webPushSenderService = new WebPushSenderService(
                pushService, pushSubscriptionDao, notificationDeliveryLogRepository, new ObjectMapper());
    }

    @Test
    void sendToUserNoRegisteredSubscriptionsNeverCallsThePushService() throws Exception {
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of());

        webPushSenderService.sendToUser(notification(), USER_ID);

        verifyNoInteractions(pushService);
        verifyNoInteractions(notificationDeliveryLogRepository);
    }

    @Test
    void sendToUserSuccessfulSendLogsAWebPushDeliveryRow() throws Exception {
        PushSubscription subscription = subscription(1L);
        HttpResponse response = httpResponseWithStatus(201);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenReturn(response);

        webPushSenderService.sendToUser(notification(), USER_ID);

        ArgumentCaptor<NotificationDeliveryLog> captor = ArgumentCaptor.forClass(NotificationDeliveryLog.class);
        verify(notificationDeliveryLogRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannelEnum.WEB_PUSH);
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatusEnum.SENT);
        assertThat(captor.getValue().getSubject()).isEqualTo("Title");
        assertThat(captor.getValue().getBody()).isEqualTo("Message");
        verify(pushSubscriptionDao, never()).deleteById(any());
    }

    @Test
    void sendToUserPushServiceReports404RemovesStaleSubscriptionWithoutLogging() throws Exception {
        PushSubscription subscription = subscription(2L);
        HttpResponse response = httpResponseWithStatus(404);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenReturn(response);

        webPushSenderService.sendToUser(notification(), USER_ID);

        verify(pushSubscriptionDao, times(1)).deleteById(2L);
        verify(notificationDeliveryLogRepository, never()).save(any());
    }

    @Test
    void sendToUserPushServiceReports410GoneRemovesStaleSubscription() throws Exception {
        PushSubscription subscription = subscription(3L);
        HttpResponse response = httpResponseWithStatus(410);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenReturn(response);

        webPushSenderService.sendToUser(notification(), USER_ID);

        verify(pushSubscriptionDao, times(1)).deleteById(3L);
        verify(notificationDeliveryLogRepository, never()).save(any());
    }

    @Test
    void sendToUserSendThrowsAnExceptionFailsSoftAndNeverPropagates() throws Exception {
        PushSubscription subscription = subscription(4L);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenThrow(new IOException("connection refused"));

        webPushSenderService.sendToUser(notification(), USER_ID);

        verify(notificationDeliveryLogRepository, never()).save(any());
        verify(pushSubscriptionDao, never()).deleteById(any());
    }

    @Test
    void sendToUserUnexpectedServerErrorStatusFailsSoftWithoutDeletingOrLogging() throws Exception {
        PushSubscription subscription = subscription(5L);
        HttpResponse response = httpResponseWithStatus(500);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenReturn(response);

        webPushSenderService.sendToUser(notification(), USER_ID);

        verify(notificationDeliveryLogRepository, never()).save(any());
        verify(pushSubscriptionDao, never()).deleteById(any());
    }

    @Test
    void sendToUserMultipleSubscriptionsEachHandledIndependently() throws Exception {
        PushSubscription stale = subscription(6L);
        PushSubscription healthy = subscription(7L);
        HttpResponse staleResponse = httpResponseWithStatus(410);
        HttpResponse healthyResponse = httpResponseWithStatus(201);
        when(pushSubscriptionDao.findByUserId(USER_ID)).thenReturn(List.of(stale, healthy));
        when(pushService.send(any()))
                .thenReturn(staleResponse)
                .thenReturn(healthyResponse);

        webPushSenderService.sendToUser(notification(), USER_ID);

        verify(pushSubscriptionDao, times(1)).deleteById(6L);
        verify(notificationDeliveryLogRepository, times(1)).save(any());
    }

    private static Notification notification() {
        return Notification.builder()
                .id(100L)
                .userId(USER_ID)
                .type(NotificationTypeEnum.SYSTEM)
                .title("Title")
                .message("Message")
                .build();
    }

    // A syntactically valid (real, unpadded base64url, uncompressed P-256 point) but otherwise
    // meaningless public key. The nl.martijndwars.webpush.Notification constructor decodes
    // p256dh as an EC point *before* pushService.send(...) (mocked, below) is ever reached, so
    // it must actually parse.
    private static final String DUMMY_P256DH = "BN4cNJBWEhzzZ0hlqWU6fV-CcEtrGCxrZSjsjbkDHCS5q1mXeW58hBT_3yarqsbWJsuitV3_WXrYgzqBFGJRF-0";
    private static final String DUMMY_AUTH = "dGVzdC1hdXRoLXNlY3JldA";

    private static PushSubscription subscription(Long id) {
        return PushSubscription.builder()
                .id(id)
                .userId(USER_ID)
                .endpoint("https://push.example.com/ep/" + id)
                .p256dhKey(DUMMY_P256DH)
                .authKey(DUMMY_AUTH)
                .build();
    }

    private static HttpResponse httpResponseWithStatus(int status) {
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(status);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }
}
