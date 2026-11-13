package com.leaseledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// We authenticate with our own JWT filter, so Spring Boot's default in-memory
// user (the one that prints a generated password at startup) is switched off.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LeaseLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaseLedgerApplication.class, args);
    }
}
