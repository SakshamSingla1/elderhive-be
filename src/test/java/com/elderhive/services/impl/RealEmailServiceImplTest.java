package com.elderhive.services.impl;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms RealEmailServiceImpl actually sends over SMTP when app.mail.enabled=true - no
 * Spring context needed, GreenMail stands in for a live SMTP server.
 */
class RealEmailServiceImplTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    @Test
    void sendsRealEmailOverSmtp() throws Exception {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());

        RealEmailServiceImpl emailService = new RealEmailServiceImpl(mailSender, "no-reply@elderhive.app");

        emailService.sendEmail("family@elderhive.app", "Reset your ElderHive password", "<p>Hello there</p>");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Reset your ElderHive password");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("family@elderhive.app");
        assertThat(GreenMailUtil.getBody(received[0])).contains("Hello there");
    }
}
