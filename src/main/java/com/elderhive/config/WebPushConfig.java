package com.elderhive.config;

import com.elderhive.webpush.VapidKeyProvider;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.PushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;

/**
 * Ported verbatim from ElderSphere's WebPushConfig. Builds the single PushService bean used
 * to send Web Push messages, keyed with the VAPID key pair VapidKeyProvider resolves.
 */
@Configuration
@RequiredArgsConstructor
public class WebPushConfig {

    private final VapidKeyProvider vapidKeyProvider;

    @Bean
    public PushService pushService() throws GeneralSecurityException {
        return new PushService(vapidKeyProvider.getPublicKey(), vapidKeyProvider.getPrivateKey(), vapidKeyProvider.getSubject());
    }
}
