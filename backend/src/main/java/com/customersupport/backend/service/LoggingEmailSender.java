package com.customersupport.backend.service;

import com.customersupport.backend.dto.OutboundEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sender used when no SMTP is configured ({@code app.email.outbound=log}, e.g. tests). Logs metadata only, never the body. */
@Component
@ConditionalOnProperty(name = "app.email.outbound", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutboundEmail email) {
        log.info("Outbound email (not actually sent) to={} subject={} messageId={} inReplyTo={}",
                email.to(), email.subject(), email.messageId(), email.inReplyTo());
    }
}
