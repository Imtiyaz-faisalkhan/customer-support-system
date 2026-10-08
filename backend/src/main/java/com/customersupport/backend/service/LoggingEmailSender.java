package com.customersupport.backend.service;

import com.customersupport.backend.dto.OutboundEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder sender until an email provider is chosen. Logs metadata only, never the body. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutboundEmail email) {
        log.info("Outbound email (not actually sent) to={} subject={}", email.to(), email.subject());
    }
}
