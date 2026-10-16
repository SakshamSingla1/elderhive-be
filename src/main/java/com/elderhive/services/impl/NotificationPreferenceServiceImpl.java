package com.elderhive.services.impl;

import com.elderhive.dtos.Notification.NotificationPreferenceItemRequest;
import com.elderhive.dtos.Notification.NotificationPreferenceResponse;
import com.elderhive.entities.NotificationPreference;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.repositories.NotificationPreferenceRepository;
import com.elderhive.services.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Override
    public List<NotificationPreferenceResponse> getPreferences(Long userId) {
        return Arrays.stream(NotificationTypeEnum.values())
                .map(type -> notificationPreferenceRepository.findByUserIdAndType(userId, type)
                        .map(this::mapToResponse)
                        .orElseGet(() -> defaultResponse(type)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<NotificationPreferenceResponse> updatePreferences(Long userId, List<NotificationPreferenceItemRequest> updates) {
        for (NotificationPreferenceItemRequest item : updates) {
            NotificationPreference preference = notificationPreferenceRepository
                    .findByUserIdAndType(userId, item.getType())
                    .orElseGet(() -> NotificationPreference.builder().userId(userId).type(item.getType()).build());
            preference.setInAppEnabled(item.isInAppEnabled());
            preference.setEmailEnabled(item.isEmailEnabled());
            preference.setSmsEnabled(item.isSmsEnabled());
            preference.setWebPushEnabled(item.isWebPushEnabled());
            notificationPreferenceRepository.save(preference);
        }
        return getPreferences(userId);
    }

    // Mirrors NotificationPreference's own documented default contract (absence of a row
    // means in-app only) - the caller gets a fully-populated row for every type either way.
    private NotificationPreferenceResponse defaultResponse(NotificationTypeEnum type) {
        return NotificationPreferenceResponse.builder()
                .type(type).inAppEnabled(true).emailEnabled(false).smsEnabled(false).webPushEnabled(false)
                .build();
    }

    private NotificationPreferenceResponse mapToResponse(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .type(preference.getType())
                .inAppEnabled(preference.isInAppEnabled())
                .emailEnabled(preference.isEmailEnabled())
                .smsEnabled(preference.isSmsEnabled())
                .webPushEnabled(preference.isWebPushEnabled())
                .build();
    }
}
