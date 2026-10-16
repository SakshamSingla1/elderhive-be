package com.elderhive.dtos.Notification;

import com.elderhive.enums.NotificationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {

    private NotificationTypeEnum type;
    private boolean inAppEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean webPushEnabled;
}
