package com.elderhive.services.impl;

import com.elderhive.dtos.Notification.NotificationPreferenceItemRequest;
import com.elderhive.dtos.Notification.NotificationPreferenceResponse;
import com.elderhive.entities.NotificationPreference;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.repositories.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @InjectMocks
    private NotificationPreferenceServiceImpl notificationPreferenceService;

    @Test
    void getPreferencesReturnsOneRowPerTypeWithDefaultsForMissingOnes() {
        NotificationPreference bookingRow = NotificationPreference.builder()
                .userId(USER_ID).type(NotificationTypeEnum.BOOKING)
                .inAppEnabled(true).emailEnabled(true).smsEnabled(false).webPushEnabled(true).build();
        when(notificationPreferenceRepository.findByUserIdAndType(anyLong(), any(NotificationTypeEnum.class)))
                .thenReturn(Optional.empty());
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.BOOKING))
                .thenReturn(Optional.of(bookingRow));

        List<NotificationPreferenceResponse> preferences = notificationPreferenceService.getPreferences(USER_ID);

        assertThat(preferences).hasSize(NotificationTypeEnum.values().length);
        NotificationPreferenceResponse booking = preferences.stream()
                .filter(p -> p.getType() == NotificationTypeEnum.BOOKING).findFirst().orElseThrow();
        assertThat(booking.isEmailEnabled()).isTrue();
        assertThat(booking.isWebPushEnabled()).isTrue();

        NotificationPreferenceResponse emergency = preferences.stream()
                .filter(p -> p.getType() == NotificationTypeEnum.EMERGENCY).findFirst().orElseThrow();
        assertThat(emergency.isInAppEnabled()).isTrue();
        assertThat(emergency.isEmailEnabled()).isFalse();
        assertThat(emergency.isSmsEnabled()).isFalse();
        assertThat(emergency.isWebPushEnabled()).isFalse();
    }

    @Test
    void updatePreferencesCreatesANewRowWhenNoneExistsYet() {
        when(notificationPreferenceRepository.findByUserIdAndType(anyLong(), any(NotificationTypeEnum.class)))
                .thenReturn(Optional.empty());

        NotificationPreferenceItemRequest item = new NotificationPreferenceItemRequest();
        item.setType(NotificationTypeEnum.BOOKING);
        item.setEmailEnabled(true);
        item.setSmsEnabled(true);

        notificationPreferenceService.updatePreferences(USER_ID, List.of(item));

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(captor.getValue().isEmailEnabled()).isTrue();
        assertThat(captor.getValue().isSmsEnabled()).isTrue();
    }

    @Test
    void updatePreferencesOverwritesAnExistingRowInPlaceRatherThanDuplicatingIt() {
        NotificationPreference existing = NotificationPreference.builder()
                .id(77L).userId(USER_ID).type(NotificationTypeEnum.EMERGENCY)
                .inAppEnabled(true).emailEnabled(false).smsEnabled(false).webPushEnabled(false).build();
        when(notificationPreferenceRepository.findByUserIdAndType(USER_ID, NotificationTypeEnum.EMERGENCY))
                .thenReturn(Optional.of(existing));

        NotificationPreferenceItemRequest item = new NotificationPreferenceItemRequest();
        item.setType(NotificationTypeEnum.EMERGENCY);
        item.setSmsEnabled(true);
        item.setWebPushEnabled(true);

        notificationPreferenceService.updatePreferences(USER_ID, List.of(item));

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(77L);
        assertThat(captor.getValue().isSmsEnabled()).isTrue();
        assertThat(captor.getValue().isWebPushEnabled()).isTrue();
    }
}
