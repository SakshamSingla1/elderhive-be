package com.elderhive.services.impl;

import com.elderhive.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Logs the "email" at INFO instead of actually sending one. The default EmailService bean
 * whenever app.mail.enabled isn't explicitly "true" - keeps local dev and CI runnable with
 * zero SMTP credentials. Never remove this fallback (see IMPLEMENTATION_PLAN.md Phase 16's
 * own instruction) - RealEmailServiceImpl is the alternative, selected by the same property.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class SimulatedEmailServiceImpl implements EmailService {

    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        log.info("Simulated email -> to: {} | subject: {} | content: {}", to, subject, htmlContent);
    }
}
