package com.elderhive.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ported from ElderSphere's own StripeConfig (same pattern as this project's own
 * CloudinaryConfig). Sets the Stripe SDK's global API key at startup (if configured) and
 * exposes {@link #isConfigured()}/{@link #isWebhookConfigured()} so PaymentServiceImpl can
 * fail clearly - rather than throw an opaque Stripe SDK error - when Stripe isn't set up.
 */
@Slf4j
@Component
public class StripeConfig {

    @Getter
    private final String secretKey;

    @Getter
    private final String publishableKey;

    @Getter
    private final String webhookSecret;

    public StripeConfig(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.publishable-key:}") String publishableKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.secretKey = secretKey;
        this.publishableKey = publishableKey;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(secretKey)) {
            log.warn("Stripe: no STRIPE_SECRET_KEY configured - payments are inert. " +
                    "Every payment endpoint will fail with PAYMENT_GATEWAY_NOT_CONFIGURED " +
                    "until it's set (get test keys at https://dashboard.stripe.com/test/apikeys).");
            return;
        }

        Stripe.apiKey = secretKey;
        if (secretKey.startsWith("sk_test_")) {
            log.info("Stripe: configured in TEST mode (sk_test_...).");
        } else if (secretKey.startsWith("sk_live_")) {
            log.info("Stripe: configured in LIVE mode (sk_live_...). Real charges will be made.");
        } else {
            log.info("Stripe: configured with a non-standard secret key prefix.");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(secretKey);
    }

    public boolean isWebhookConfigured() {
        return StringUtils.hasText(webhookSecret);
    }
}
