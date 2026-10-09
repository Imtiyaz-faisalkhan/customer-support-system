package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Verifies Resend webhook signatures (Svix scheme): HMAC-SHA256 over "{svix-id}.{svix-timestamp}.{raw body}" with
 * the base64-decoded part of the "whsec_..." secret, compared in constant time to any "v1,{base64}" entry in the
 * space-separated svix-signature header. Timestamps older/newer than 5 minutes are rejected (replay protection).
 * Fails closed when no secret is configured.
 */
@Component
public class ResendWebhookVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final EmailProperties emailProperties;
    private final Clock clock;

    public ResendWebhookVerifier(EmailProperties emailProperties, Clock clock) {
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    public boolean isValid(String id, String timestamp, String signatureHeader, String rawBody) {
        String secret = emailProperties.resend() == null ? null : emailProperties.resend().webhookSecret();
        if (secret == null || secret.isBlank() || id == null || timestamp == null || signatureHeader == null
                || rawBody == null) {
            return false;
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - sentAt) > TOLERANCE.toSeconds()) {
            return false;
        }
        byte[] expected;
        try {
            byte[] key = Base64.getDecoder().decode(secret.startsWith("whsec_") ? secret.substring(6) : secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            expected = Base64.getEncoder().encode(
                    mac.doFinal((id + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            return false;
        }
        for (String entry : signatureHeader.trim().split(" ")) {
            int comma = entry.indexOf(',');
            if (comma > 0 && entry.substring(0, comma).equals("v1")
                    && MessageDigest.isEqual(expected, entry.substring(comma + 1).getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }
}
