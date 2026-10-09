package com.customersupport.backend.controller;

import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import com.customersupport.backend.service.InboundEmailParser;
import com.customersupport.backend.service.InboundEmailService;
import com.customersupport.backend.service.ResendClient;
import com.customersupport.backend.service.ResendWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Resend receiving webhook ({@code email.received}). Public in SecurityConfig; authenticity comes from Resend's
 * Svix signature over the raw body (ResendWebhookVerifier). The event only carries metadata, so the full email is
 * fetched from Resend's API and then goes through the same pipeline as every other inbound email.
 * <p>
 * Status codes drive Resend's retries: 401 bad signature, 502 when Resend's API can't be reached (retried later),
 * 200 otherwise, including emails we deliberately drop, so they aren't redelivered forever.
 */
@RestController
@RequestMapping("/api/webhooks/resend")
public class ResendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final ResendWebhookVerifier verifier;
    private final ResendClient resendClient;
    private final InboundEmailParser parser;
    private final InboundEmailService inboundEmailService;
    private final ObjectMapper objectMapper;

    public ResendWebhookController(ResendWebhookVerifier verifier, ResendClient resendClient,
                                   InboundEmailParser parser, InboundEmailService inboundEmailService,
                                   ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.resendClient = resendClient;
        this.parser = parser;
        this.inboundEmailService = inboundEmailService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "svix-id", required = false) String id,
            @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
            @RequestHeader(value = "svix-signature", required = false) String signature,
            @RequestBody String rawBody) {
        if (!verifier.isValid(id, timestamp, signature, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }
        String emailId;
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            if (!"email.received".equals(event.path("type").asString(""))) {
                return ResponseEntity.ok(Map.of("outcome", "IGNORED")); // other event types we didn't subscribe to
            }
            emailId = event.path("data").path("email_id").asString("");
        } catch (JacksonException ex) {
            log.warn("Dropping unparseable Resend webhook: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("outcome", "IGNORED"));
        }
        if (emailId.isBlank()) {
            return ResponseEntity.ok(Map.of("outcome", "IGNORED"));
        }

        ResendReceivedEmail received;
        try {
            received = resendClient.getReceivedEmail(emailId);
        } catch (RestClientException ex) {
            log.warn("Could not fetch received email {} from Resend (Resend will retry): {}", emailId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Could not fetch email"));
        }
        InboundEmail email;
        try {
            email = parser.parse(received);
        } catch (IllegalArgumentException ex) {
            log.warn("Dropping unparseable Resend email {}: {}", emailId, ex.getMessage());
            return ResponseEntity.ok(Map.of("outcome", "IGNORED"));
        }
        InboundEmailService.Result result = inboundEmailService.receive(email);
        log.info("Inbound email (Resend) from={} outcome={} ticketId={}", email.fromAddress(), result.outcome(),
                result.ticketId());
        return ResponseEntity.ok(result.ticketId() == null
                ? Map.of("outcome", result.outcome())
                : Map.of("outcome", result.outcome(), "ticketId", result.ticketId()));
    }
}
