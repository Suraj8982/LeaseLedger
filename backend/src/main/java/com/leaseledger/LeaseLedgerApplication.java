package com.leaseledger;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// We authenticate with our own JWT filter, so Spring Boot's default in-memory
// user (the one that prints a generated password at startup) is switched off.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LeaseLedgerApplication {

    public static void main(String[] args) {
        // Run in UTC whatever machine this is. Timestamps then mean the same thing on a
        // laptop in India and a server in any AWS region. It also avoids a real failure:
        // the PostgreSQL driver sends the JVM's zone name when it connects, and Windows
        // reports India as "Asia/Calcutta", a legacy name newer PostgreSQL images reject.
        // Business dates ("is this overdue today?") use the Clock in TimeConfig instead.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LeaseLedgerApplication.class, args);
    }
}
