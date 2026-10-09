package com.customersupport.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Injectable clock so time-dependent logic (e.g. dashboard windows) can be tested with a fixed "now". */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        // JVM default zone, matching the zone Hibernate uses for the LocalDateTime ticket timestamps.
        return Clock.systemDefaultZone();
    }
}
