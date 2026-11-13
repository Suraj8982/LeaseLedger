package com.leaseledger.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * The one source of "now" and "today" for business rules.
     *
     * <p>"Is this invoice overdue?" depends on what today's date is, and that depends on
     * the time zone: at 01:00 in Dubai it is still yesterday in UTC. The server's own
     * zone is whatever AWS region it runs in, so the business zone is configured
     * explicitly. Injecting a Clock also lets tests fix the date.
     */
    @Bean
    Clock clock(@Value("${leaseledger.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
