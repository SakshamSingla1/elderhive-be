package com.elderhive.services.impl;

import com.elderhive.entities.Notification;
import com.elderhive.entities.NotificationDeliveryLog;
import com.elderhive.entities.NotificationPreference;
import com.elderhive.entities.NotificationTemplate;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.NotificationChannelEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationDeliveryLogRepository;
import com.elderhive.repositories.NotificationPreferenceRepository;
import com.elderhive.repositories.NotificationRepository;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.RenderedNotification;
import com.elderhive.services.TemplateRenderer;
import com.elderhive.webpush.WebPushSenderService;
import com.elderhive.dtos.Notification.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final RenderedNotification RENDERED = new RenderedNotification("Subject", "Body");

    @Mock
    private TemplateRenderer templateRenderer;

    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private NotificationDeliveryLogRepository notificationDeliveryLogRepository;

    @Mock
    private WebPushSenderService webPushSenderService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User sampleUser() {
        return User.builder().id(USER_ID).email("user@example.com").passwordHash("hashed")
                .userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build();
    }

    private NotificationTemplate template(NotificationTypeEnum type, boolean sms, boolean email, boolean webPush) {
        return NotificationTemplate.builder()
                .id(1L).templateKey("key").type(type)
                .isInApp(true).isSms(sms).isEmail(email).isWebPush(webPush)
                .build();
    }

    private void stubCommon(NotificationTemplate template) throws GenericException {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sampleUser()));
        when(templateRenderer.render(eq("key"), any())).thenReturn(RENDERED);
        when(notificationTemplateRepository.findByTemplateKey("key")).thenReturn(Optional.of(template));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(100L);
            return n;
        });
    }

    @Test
    void alwaysWritesTheNotificationRowAndAnInAppDeliveryLogRegardlessOfTemplateOrPreference() throws GenericException {
        stubCommon(template(NotificationTypeEnum.SYSTEM, false, false, false));
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.SYSTEM))
                .thenReturn(Optional.empty());

        notificationService.send("key", Map.of(), USER_ID);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationTypeEnum.SYSTEM);
        assertThat(notificationCaptor.getValue().getTitle()).isEqualTo("Subject");
        assertThat(notificationCaptor.getValue().getMessage()).isEqualTo("Body");
        assertThat(notificationCaptor.getValue().isRead()).isFalse();

        ArgumentCaptor<NotificationDeliveryLog> logCaptor = ArgumentCaptor.forClass(NotificationDeliveryLog.class);
        verify(notificationDeliveryLogRepository, times(1)).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getChannel()).isEqualTo(NotificationChannelEnum.IN_APP);
        assertThat(logCaptor.getValue().getNotificationId()).isEqualTo(100L);
    }

    @Test
    void doesNotWriteSmsOrEmailLogsWhenNoPreferenceRowExists() throws GenericException {
        stubCommon(template(NotificationTypeEnum.BOOKING, true, true, true));
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.BOOKING))
                .thenReturn(Optional.empty());

        notificationService.send("key", Map.of(), USER_ID);

        // Only the unconditional IN_APP log - absence of a preference row means everything
        // else (sms/email/web-push) defaults off.
        verify(notificationDeliveryLogRepository, times(1)).save(any());
        verify(webPushSenderService, never()).sendToUser(any(), any());
    }

    @Test
    void writesSmsAndEmailLogsOnlyWhenBothTemplateApplicableAndPreferenceEnabled() throws GenericException {
        stubCommon(template(NotificationTypeEnum.BOOKING, true, true, false));
        NotificationPreference preference = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationTypeEnum.BOOKING)
                .inAppEnabled(true).smsEnabled(true).emailEnabled(true).webPushEnabled(false).build();
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.BOOKING))
                .thenReturn(Optional.of(preference));

        notificationService.send("key", Map.of(), USER_ID);

        ArgumentCaptor<NotificationDeliveryLog> logCaptor = ArgumentCaptor.forClass(NotificationDeliveryLog.class);
        verify(notificationDeliveryLogRepository, times(3)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(NotificationDeliveryLog::getChannel)
                .containsExactlyInAnyOrder(NotificationChannelEnum.IN_APP, NotificationChannelEnum.SIMULATED_SMS,
                        NotificationChannelEnum.SIMULATED_EMAIL);
    }

    @Test
    void doesNotWriteSmsLogWhenPreferenceEnabledButTemplateDoesNotSupportSms() throws GenericException {
        stubCommon(template(NotificationTypeEnum.BOOKING, false, false, false));
        NotificationPreference preference = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationTypeEnum.BOOKING)
                .smsEnabled(true).emailEnabled(true).build();
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.BOOKING))
                .thenReturn(Optional.of(preference));

        notificationService.send("key", Map.of(), USER_ID);

        // Template doesn't support SMS/email even though the user's preference would allow
        // it - "applicable" (template) and "enabled" (preference) must both hold.
        verify(notificationDeliveryLogRepository, times(1)).save(any());
    }

    @Test
    void callsWebPushSenderOnlyWhenTemplateSupportsItAndPreferenceEnablesIt() throws GenericException {
        stubCommon(template(NotificationTypeEnum.EMERGENCY, false, false, true));
        NotificationPreference preference = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationTypeEnum.EMERGENCY).webPushEnabled(true).build();
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.EMERGENCY))
                .thenReturn(Optional.of(preference));

        notificationService.send("key", Map.of(), USER_ID);

        verify(webPushSenderService, times(1)).sendToUser(any(Notification.class), eq(USER_ID));
    }

    @Test
    void doesNotCallWebPushSenderWhenPreferenceDisablesItEvenIfTemplateSupportsIt() throws GenericException {
        stubCommon(template(NotificationTypeEnum.EMERGENCY, false, false, true));
        NotificationPreference preference = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationTypeEnum.EMERGENCY).webPushEnabled(false).build();
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.EMERGENCY))
                .thenReturn(Optional.of(preference));

        notificationService.send("key", Map.of(), USER_ID);

        verify(webPushSenderService, never()).sendToUser(any(), any());
    }

    @Test
    void broadcastsTheSavedNotificationOverItsOwnTopic() throws GenericException {
        stubCommon(template(NotificationTypeEnum.SYSTEM, false, false, false));
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.SYSTEM))
                .thenReturn(Optional.empty());

        notificationService.send("key", Map.of(), USER_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/notifications/" + USER_ID), captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(100L);
    }

    @Test
    void throwsUserNotFoundWhenTargetUserDoesNotExist() throws GenericException {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.send("key", Map.of(), USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);

        verify(templateRenderer, never()).render(anyString(), any());
    }

    @Test
    void propagatesTemplateRendererExceptionsWithoutWritingAnyRows() throws GenericException {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sampleUser()));
        when(templateRenderer.render(eq("missing"), any()))
                .thenThrow(new GenericException(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND, "not found"));

        assertThatThrownBy(() -> notificationService.send("missing", Map.of(), USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND);

        verify(notificationRepository, never()).save(any());
        verify(notificationDeliveryLogRepository, never()).save(any());
    }

    @Test
    void getByUserMapsEveryEntityFieldOntoTheResponse() {
        Notification notification = Notification.builder()
                .id(5L).userId(USER_ID).type(NotificationTypeEnum.SYSTEM)
                .title("Title").message("Message").link("/somewhere").isRead(true).build();
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any())).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getByUser(USER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        NotificationResponse response = result.getContent().get(0);
        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getType()).isEqualTo(NotificationTypeEnum.SYSTEM);
        assertThat(response.getTitle()).isEqualTo("Title");
        assertThat(response.getMessage()).isEqualTo("Message");
        assertThat(response.getLink()).isEqualTo("/somewhere");
        assertThat(response.isRead()).isTrue();
    }

    @Test
    void getUnreadCountDelegatesStraightToTheRepository() {
        when(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

        assertThat(notificationService.getUnreadCount(USER_ID)).isEqualTo(3L);
    }

    @Test
    void markAsReadFlipsAnUnreadNotificationBelongingToTheCaller() throws GenericException {
        Notification notification = Notification.builder().id(5L).userId(USER_ID).isRead(false).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(5L, USER_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
    }

    @Test
    void markAsReadIsANoOpForAnAlreadyReadNotification() throws GenericException {
        Notification notification = Notification.builder().id(5L).userId(USER_ID).isRead(true).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(5L, USER_ID);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadThrowsNotFoundForAnUnknownId() {
        when(notificationRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(5L, USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsReadThrowsNotFoundRatherThanLeakingThatItBelongsToSomeoneElse() {
        Notification notification = Notification.builder().id(5L).userId(999L).isRead(false).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(5L, USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_NOT_FOUND);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsReadFlipsEveryUnreadNotificationForTheUser() {
        Notification first = Notification.builder().id(1L).userId(USER_ID).isRead(false).build();
        Notification second = Notification.builder().id(2L).userId(USER_ID).isRead(false).build();
        when(notificationRepository.findByUserIdAndIsReadFalse(USER_ID)).thenReturn(List.of(first, second));

        notificationService.markAllAsRead(USER_ID);

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(notificationRepository, times(1)).saveAll(List.of(first, second));
    }

    @Test
    void markAllAsReadIsANoOpWhenNothingIsUnread() {
        when(notificationRepository.findByUserIdAndIsReadFalse(USER_ID)).thenReturn(List.of());

        notificationService.markAllAsRead(USER_ID);

        verify(notificationRepository, times(1)).saveAll(List.of());
    }
}
