package com.elderhive.sms;

import com.elderhive.enums.NotificationChannelEnum;

/**
 * The outcome of one SmsSenderService.send() call - channelUsed tells the caller
 * (NotificationServiceImpl) which NotificationChannelEnum to write to the delivery log,
 * since a single SmsSenderService bean type is wired regardless of which implementation is
 * actually active (see SimulatedSmsSenderService/TwilioSmsSenderService's own
 * @ConditionalOnProperty selection) - the caller has no other way to tell them apart.
 */
public record SmsSendResult(boolean success, NotificationChannelEnum channelUsed,
                             String providerMessageId, String errorMessage) {

    public static SmsSendResult success(NotificationChannelEnum channelUsed, String providerMessageId) {
        return new SmsSendResult(true, channelUsed, providerMessageId, null);
    }

    public static SmsSendResult failure(NotificationChannelEnum channelUsed, String errorMessage) {
        return new SmsSendResult(false, channelUsed, null, errorMessage);
    }
}
