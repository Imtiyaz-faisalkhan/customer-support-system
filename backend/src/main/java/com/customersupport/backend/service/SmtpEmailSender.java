package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.OutboundEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * Sends customer emails over SMTP ({@code spring.mail.*}): Mailpit locally, Resend's SMTP relay in real use.
 * SMTP rather than Resend's Web API keeps full control of the threading headers and stays provider-portable.
 */
@Component
@ConditionalOnProperty(name = "app.email.outbound", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public void send(OutboundEmail email) {
        MimeMessage message = new MimeMessage(mailSender.createMimeMessage().getSession()) {
            // Jakarta Mail replaces Message-ID with a random one on send; keep ours so the customer's reply
            // (In-Reply-To: <ours>) can be matched back to this ticket.
            @Override
            protected void updateMessageID() throws MessagingException {
                if (email.messageId() != null) {
                    setHeader("Message-ID", email.messageId());
                } else {
                    super.updateMessageID();
                }
            }
        };
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(emailProperties.supportAddress(), emailProperties.supportName());
            helper.setReplyTo(emailProperties.supportAddress());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.body(), false);
            if (email.inReplyTo() != null) {
                message.setHeader("In-Reply-To", email.inReplyTo());
            }
            if (email.references() != null) {
                message.setHeader("References", email.references());
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not build email to " + email.to(), ex);
        }
        mailSender.send(message);
    }
}
