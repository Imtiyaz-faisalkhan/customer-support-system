package com.customersupport.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email settings ({@code app.email.*}). Secrets come from environment variables in application.properties;
 * never commit real values. Which outbound sender is active is chosen by {@code app.email.outbound}
 * ({@code smtp} or {@code log}), see SmtpEmailSender / LoggingEmailSender. Inbound email arrives via Resend.
 */
@ConfigurationProperties("app.email")
public record EmailProperties(String supportAddress, String supportName, Resend resend,
                              boolean rejectReservedRecipients) {

    /**
     * Resend (resend.com) receiving: {@code webhookSecret} ("whsec_...") verifies the email.received webhook,
     * {@code apiKey} ("re_...") fetches the full email from {@code apiBaseUrl}. Sending via Resend is plain SMTP.
     */
    public record Resend(String apiKey, String webhookSecret, String apiBaseUrl) {
    }

    /** Domain part of the support address; used for the Message-IDs of emails we send. */
    public String domain() {
        return supportAddress.substring(supportAddress.indexOf('@') + 1);
    }
}
