package com.elderhive.services.impl;

import com.elderhive.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends real mail over SMTP via spring.mail.* (already configured from Phase 0/1) through
 * Spring's JavaMailSender. The EmailService bean whenever app.mail.enabled=true - the only
 * other implementation, SimulatedEmailServiceImpl, is excluded by the same property so
 * exactly one bean is ever active.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class RealEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public RealEmailServiceImpl(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }
}
