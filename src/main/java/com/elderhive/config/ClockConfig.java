package com.elderhive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable {@link Clock} bean so time-sensitive, in-memory components (currently
 * just {@code presence.PresenceTracker}'s heartbeat-staleness check) can be driven by a
 * fixed/controllable clock in tests instead of a hard-coded {@code Instant.now()}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
