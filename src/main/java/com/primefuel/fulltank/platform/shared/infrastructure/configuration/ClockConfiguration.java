package com.primefuel.fulltank.platform.shared.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable clock so time-dependent policies (for example the refill episode evaluation) can be
 * driven deterministically in tests instead of reading {@code Instant.now()} inline.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
