package com.elderhive.sms;

import com.elderhive.enums.NotificationChannelEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the real sender is invoked (with a mocked Twilio client, never a real account or
 * network call) when app.sms.enabled=true, and that a provider failure surfaces as a FAILED
 * SmsSendResult rather than an exception.
 */
@ExtendWith(MockitoExtension.class)
class TwilioSmsSenderServiceTest {

    private static final String FROM_NUMBER = "+15551234567";

    @Mock
    private TwilioMessageClient twilioMessageClient;

    @Test
    void sendsThroughTheMockedTwilioClientAndReturnsTheRealSmsChannel() {
        when(twilioMessageClient.sendMessage(eq("+15559998888"), eq(FROM_NUMBER), eq("Your booking is confirmed")))
                .thenReturn("SM123abc");

        TwilioSmsSenderService smsSenderService = new TwilioSmsSenderService(twilioMessageClient, FROM_NUMBER);
        SmsSendResult result = smsSenderService.send("+15559998888", "Your booking is confirmed");

        assertThat(result.success()).isTrue();
        assertThat(result.channelUsed()).isEqualTo(NotificationChannelEnum.SMS);
        assertThat(result.providerMessageId()).isEqualTo("SM123abc");
        verify(twilioMessageClient).sendMessage("+15559998888", FROM_NUMBER, "Your booking is confirmed");
    }

    @Test
    void aProviderFailureIsReportedAsAFailedResultRatherThanThrowing() {
        when(twilioMessageClient.sendMessage(eq("+15559998888"), eq(FROM_NUMBER), eq("body")))
                .thenThrow(new RuntimeException("Twilio error: invalid number"));

        TwilioSmsSenderService smsSenderService = new TwilioSmsSenderService(twilioMessageClient, FROM_NUMBER);
        SmsSendResult result = smsSenderService.send("+15559998888", "body");

        assertThat(result.success()).isFalse();
        assertThat(result.channelUsed()).isEqualTo(NotificationChannelEnum.SMS);
        assertThat(result.errorMessage()).contains("invalid number");
    }
}
