package com.elderhive.dtos.Notification;

import com.elderhive.enums.NotificationTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationPreferenceItemRequest {

    @NotNull(message = "Notification type is required")
    private NotificationTypeEnum type;

    private boolean inAppEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean webPushEnabled;
}
