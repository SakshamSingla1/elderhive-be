package com.elderhive.sms;

import com.elderhive.enums.NotificationChannelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Logs the "SMS" at INFO instead of actually sending one - matches NotificationServiceImpl's
 * pre-Prompt-102 behavior exactly. The default SmsSenderService bean whenever app.sms.enabled
 * isn't explicitly "true" - keeps local dev and CI runnable with zero Twilio credentials.
 * Never remove this fallback (IMPLEMENTATION_PLAN.md Phase 16's own instruction) -
 * TwilioSmsSenderService is the alternative, selected by the same property.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "false", matchIfMissing = true)
public class SimulatedSmsSenderService implements SmsSenderService {

    @Override
    public SmsSendResult send(String toPhoneNumber, String body) {
        log.info("Simulated SMS to {}: {}", toPhoneNumber, body);
        return SmsSendResult.success(NotificationChannelEnum.SIMULATED_SMS, null);
    }
}
