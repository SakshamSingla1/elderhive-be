package com.elderhive.services.impl;

import com.elderhive.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simulated — logs the "email" at INFO instead of actually sending one. This is a known
 * MVP placeholder: no SMTP wiring, no JavaMailSender dependency. A real implementation
 * (SMTP via the spring-boot-starter-mail dependency and spring.mail.* config already
 * present from Phase 0) is deliberately deferred to a later phase, not built now.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        log.info("Simulated email -> to: {} | subject: {} | content: {}", to, subject, htmlContent);
    }
}
