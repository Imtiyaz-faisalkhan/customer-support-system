package com.customersupport.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email settings ({@code app.email.*}). Secrets come from environment variables in application.properties;
 * never commit real values. Which outbound sender is active is chosen by {@code app.email.outbound}
 * ({@code smtp} or {@code log}), see SmtpEmailSender / LoggingEmailSender; {@code app.email.inbound=imap} turns on
 * the mailbox poller (otherwise only the webhook receives email).
 */
@ConfigurationProperties("app.email")
public record EmailProperties(String supportAddress, String supportName, Webhook webhook, Imap imap,
                              Resend resend) {

    /** HTTP Basic credentials SendGrid must send to the inbound webhook (set in the Inbound Parse URL). */
    public record Webhook(String username, String password) {
    }

    /**
     * The support mailbox ImapMailboxPoller reads (active when {@code app.email.inbound=imap}). Every message in
     * {@code folder} is imported, then moved to {@code processedFolder}.
     */
    public record Imap(String host, int port, boolean ssl, String username, String password, String folder,
                       String processedFolder, int batchSize) {
    }

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
