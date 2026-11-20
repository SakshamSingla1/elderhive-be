package com.elderhive.sms;

import com.elderhive.enums.NotificationChannelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Sends real SMS via Twilio. The SmsSenderService bean whenever app.sms.enabled=true - the
 * only other implementation, SimulatedSmsSenderService, is excluded by the same property so
 * exactly one bean is ever active. A send failure (bad number, Twilio outage, etc.) is
 * caught here rather than thrown, matching EmailService's callers never needing a try/catch
 * - NotificationServiceImpl just checks the returned SmsSendResult.success() to decide
 * SENT vs FAILED for the delivery log.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "true")
public class TwilioSmsSenderService implements SmsSenderService {

    private final TwilioMessageClient twilioMessageClient;
    private final String fromNumber;

    public TwilioSmsSenderService(TwilioMessageClient twilioMessageClient,
                                   @Value("${app.sms.twilio.from-number}") String fromNumber) {
        this.twilioMessageClient = twilioMessageClient;
        this.fromNumber = fromNumber;
    }

    @Override
    public SmsSendResult send(String toPhoneNumber, String body) {
        try {
            String messageSid = twilioMessageClient.sendMessage(toPhoneNumber, fromNumber, body);
            return SmsSendResult.success(NotificationChannelEnum.SMS, messageSid);
        } catch (Exception e) {
            log.warn("SMS send to {} failed: {}", toPhoneNumber, e.getMessage());
            return SmsSendResult.failure(NotificationChannelEnum.SMS, e.getMessage());
        }
    }
}
