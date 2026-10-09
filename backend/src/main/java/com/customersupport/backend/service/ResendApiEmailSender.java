package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.OutboundEmail;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Sends customer emails through Resend's HTTP API ({@code app.email.outbound=resend-api}). Used on Render, whose
 * free tier blocks outbound SMTP ports, so SmtpEmailSender can't reach smtp.resend.com there.
 */
@Component
@ConditionalOnProperty(name = "app.email.outbound", havingValue = "resend-api")
public class ResendApiEmailSender implements EmailSender {

    private final ResendClient resendClient;
    private final EmailProperties emailProperties;

    public ResendApiEmailSender(ResendClient resendClient, EmailProperties emailProperties) {
        this.resendClient = resendClient;
        this.emailProperties = emailProperties;
    }

    @Override
    public void send(OutboundEmail email) {
        String from = emailProperties.supportName() + " <" + emailProperties.supportAddress() + ">";
        try {
            resendClient.sendEmail(email, from, emailProperties.supportAddress());
        } catch (RestClientException ex) {
            // A MailException, like SMTP failures: rolls the reply back and maps to 502 (GlobalExceptionHandler).
            throw new MailSendException("Resend could not send the email to " + email.to(), ex);
        }
    }
}
