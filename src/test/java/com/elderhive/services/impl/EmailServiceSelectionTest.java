package com.elderhive.services.impl;

import com.elderhive.services.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves app.mail.enabled selects exactly one EmailService bean - callers never need to
 * know which implementation is active. No SMTP server involved; RealEmailServiceImplTest
 * covers the actual sending behavior with GreenMail.
 */
class EmailServiceSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimulatedEmailServiceImpl.class, RealEmailServiceImpl.class)
            .withBean(JavaMailSender.class, () -> Mockito.mock(JavaMailSender.class))
            .withPropertyValues("app.mail.from=no-reply@elderhive.app");

    @Test
    void simulatedBeanSelectedWhenPropertyAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context.getBean(EmailService.class)).isInstanceOf(SimulatedEmailServiceImpl.class);
        });
    }

    @Test
    void simulatedBeanSelectedWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("app.mail.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context.getBean(EmailService.class)).isInstanceOf(SimulatedEmailServiceImpl.class);
        });
    }

    @Test
    void realBeanSelectedWhenEnabled() {
        contextRunner.withPropertyValues("app.mail.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context.getBean(EmailService.class)).isInstanceOf(RealEmailServiceImpl.class);
        });
    }
}
