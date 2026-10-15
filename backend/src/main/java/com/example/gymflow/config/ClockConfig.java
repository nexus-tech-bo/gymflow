package com.example.gymflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Application clock in the gym time zone; "today" for memberships, check-ins and reports comes from it.
 */
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(@Value("${app.timezone:America/Lima}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
