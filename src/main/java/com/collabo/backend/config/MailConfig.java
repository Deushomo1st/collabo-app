package com.collabo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Mail configuration. Connection settings (host, port, credentials) stay in
 * application.properties under spring.mail.* where Spring Boot auto-configures
 * the JavaMailSender; this holds the sender identity so it isn't hardcoded
 * inside EmailService business logic.
 */
@Configuration
public class MailConfig {

    /**
     * Must be an address on the Resend-verified domain, or sends are rejected.
     */
    @Value("${app.mail.from:Collabo Team <onboarding@collaboapp.pro>}")
    private String fromAddress;

    public String getFromAddress() {
        return fromAddress;
    }
}
