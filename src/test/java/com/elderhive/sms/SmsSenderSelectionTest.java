package com.elderhive.sms;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves app.sms.enabled selects exactly one SmsSenderService bean - NotificationServiceImpl
 * never needs to know which implementation is active. No real Twilio account/network
 * involved; TwilioSmsSenderServiceTest covers the actual send behavior with a mocked client.
 */
class SmsSenderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimulatedSmsSenderService.class, TwilioSmsSenderService.class)
            .withBean(TwilioMessageClient.class, () -> Mockito.mock(TwilioMessageClient.class))
            .withPropertyValues("app.sms.twilio.from-number=+15551234567");

    @Test
    void simulatedBeanSelectedWhenPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SmsSenderService.class);
            assertThat(context.getBean(SmsSenderService.class)).isInstanceOf(SimulatedSmsSenderService.class);
        });
    }

    @Test
    void simulatedBeanSelectedWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("app.sms.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(SmsSenderService.class);
            assertThat(context.getBean(SmsSenderService.class)).isInstanceOf(SimulatedSmsSenderService.class);
        });
    }

    @Test
    void twilioBeanSelectedWhenEnabled() {
        contextRunner.withPropertyValues("app.sms.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(SmsSenderService.class);
            assertThat(context.getBean(SmsSenderService.class)).isInstanceOf(TwilioSmsSenderService.class);
        });
    }
}
