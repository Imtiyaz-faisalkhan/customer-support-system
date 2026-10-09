package com.customersupport.backend.controller;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.service.InboundEmailParser;
import com.customersupport.backend.service.InboundEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * SendGrid Inbound Parse webhook. Public in SecurityConfig (SendGrid has no session); instead SendGrid sends
 * dedicated HTTP Basic credentials, configured in the Inbound Parse destination URL
 * (https://user:pass@host/api/webhooks/sendgrid/inbound), checked here.
 */
@RestController
@RequestMapping("/api/webhooks/sendgrid")
public class InboundEmailWebhookController {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailWebhookController.class);

    private final InboundEmailParser parser;
    private final InboundEmailService inboundEmailService;
    private final EmailProperties emailProperties;

    public InboundEmailWebhookController(InboundEmailParser parser, InboundEmailService inboundEmailService,
                                         EmailProperties emailProperties) {
        this.parser = parser;
        this.inboundEmailService = inboundEmailService;
        this.emailProperties = emailProperties;
    }

    // Attachment parts (out of MVP scope) are multipart file parts, so they don't appear in this field map.
    @PostMapping(value = "/inbound", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> inbound(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam Map<String, String> fields) {
        if (!credentialsMatch(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        InboundEmail email;
        try {
            email = parser.parse(fields);
        } catch (IllegalArgumentException ex) {
            // A malformed email will never parse; 200 so SendGrid doesn't keep retrying it.
            log.warn("Dropping unparseable inbound email: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("outcome", "IGNORED"));
        }
        InboundEmailService.Result result = inboundEmailService.receive(email);
        log.info("Inbound email from={} outcome={} ticketId={}", email.fromAddress(), result.outcome(),
                result.ticketId());
        return ResponseEntity.ok(result.ticketId() == null
                ? Map.of("outcome", result.outcome())
                : Map.of("outcome", result.outcome(), "ticketId", result.ticketId()));
    }

    /** Fails closed: with no password configured, every request is rejected. Constant-time comparison. */
    private boolean credentialsMatch(String authorization) {
        EmailProperties.Webhook webhook = emailProperties.webhook();
        if (webhook == null || webhook.password() == null || webhook.password().isBlank()
                || authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        byte[] expected = (webhook.username() + ":" + webhook.password()).getBytes(StandardCharsets.UTF_8);
        byte[] actual;
        try {
            actual = Base64.getDecoder().decode(authorization.substring("Basic ".length()).trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }
}
