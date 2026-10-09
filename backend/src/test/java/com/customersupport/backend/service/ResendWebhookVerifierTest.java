package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ResendWebhookVerifierTest {

    // Official test vector from Svix's manual-verification docs.
    private static final String SECRET = "whsec_plJ3nmyCDGBKInavdOK15jsl";
    private static final String ID = "msg_loFOjxBNrRLzqYUf";
    private static final String TIMESTAMP = "1731705121";
    private static final String BODY = "{\"event_type\":\"ping\",\"data\":{\"success\":true}}";
    private static final String SIGNATURE = "v1,rAvfW3dJ/X/qxhsaXPOyyCGmRKsaKWcsNccKXlIktD0=";

    private static ResendWebhookVerifier verifierAt(long epochSecond, String secret) {
        return new ResendWebhookVerifier(
                new EmailProperties("support@test.example", "Support",
                        new EmailProperties.Resend("re_x", secret, null), false),
                Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    @Test
    void acceptsTheOfficialSvixTestVector() {
        assertThat(verifierAt(1731705121, SECRET).isValid(ID, TIMESTAMP, SIGNATURE, BODY)).isTrue();
    }

    @Test
    void acceptsWhenAnyOfSeveralSignaturesMatches() {
        String rotated = "v1,c29tZSBvbGQgc2lnbmF0dXJl " + SIGNATURE;

        assertThat(verifierAt(1731705121, SECRET).isValid(ID, TIMESTAMP, rotated, BODY)).isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongSecretAndMissingSecret() {
        assertThat(verifierAt(1731705121, SECRET).isValid(ID, TIMESTAMP, SIGNATURE, BODY.replace("true", "false")))
                .isFalse();
        assertThat(verifierAt(1731705121, "whsec_c29tZW90aGVyc2VjcmV0").isValid(ID, TIMESTAMP, SIGNATURE, BODY))
                .isFalse();
        assertThat(verifierAt(1731705121, "").isValid(ID, TIMESTAMP, SIGNATURE, BODY)).isFalse();
        assertThat(verifierAt(1731705121, SECRET).isValid(null, TIMESTAMP, SIGNATURE, BODY)).isFalse();
    }

    @Test
    void rejectsReplayedOldOrFutureTimestamps() {
        long tolerance = ResendWebhookVerifier.TOLERANCE.toSeconds();

        assertThat(verifierAt(1731705121 + tolerance + 1, SECRET).isValid(ID, TIMESTAMP, SIGNATURE, BODY)).isFalse();
        assertThat(verifierAt(1731705121 - tolerance - 1, SECRET).isValid(ID, TIMESTAMP, SIGNATURE, BODY)).isFalse();
        assertThat(verifierAt(1731705121 + tolerance, SECRET).isValid(ID, TIMESTAMP, SIGNATURE, BODY)).isTrue();
    }
}
