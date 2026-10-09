package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resend's API: fetches received emails (the email.received webhook only carries metadata; the body and headers
 * have to be retrieved separately with the API key) and, on Render, sends ours (see ResendApiEmailSender).
 */
@Component
public class ResendClient {

    private final RestClient restClient;

    @Autowired
    public ResendClient(EmailProperties emailProperties) {
        this(RestClient.builder().requestFactory(timeouts()), emailProperties);
    }

    /**
     * Each fetch holds a webhook request thread, and Resend gives up on a webhook after ~15s anyway (then retries),
     * so a stalled API must fail fast instead of piling up threads.
     */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    /** For tests: lets MockRestServiceServer bind to the builder. */
    ResendClient(RestClient.Builder builder, EmailProperties emailProperties) {
        EmailProperties.Resend resend = emailProperties.resend();
        this.restClient = builder
                .baseUrl(resend == null || resend.apiBaseUrl() == null ? "https://api.resend.com" : resend.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (resend == null ? "" : resend.apiKey()))
                .build();
    }

    /** @throws org.springframework.web.client.RestClientException if Resend can't be reached or rejects the key */
    public ResendReceivedEmail getReceivedEmail(String emailId) {
        return restClient.get()
                .uri("/emails/receiving/{id}", emailId)
                .retrieve()
                .body(ResendReceivedEmail.class);
    }

    /**
     * Sends an email through {@code POST /emails}. Threading headers go in as custom headers; Resend may still
     * replace our Message-ID, which the "[#id-TOKEN]" subject tag covers.
     *
     * @throws org.springframework.web.client.RestClientException if Resend can't be reached or rejects the email
     */
    public void sendEmail(OutboundEmail email, String from, String replyTo) {
        Map<String, String> headers = new LinkedHashMap<>();
        putIfPresent(headers, "Message-ID", email.messageId());
        putIfPresent(headers, "In-Reply-To", email.inReplyTo());
        putIfPresent(headers, "References", email.references());
        if (email.autoReply()) {
            headers.put("Auto-Submitted", "auto-replied");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("from", from);
        request.put("to", List.of(email.to()));
        request.put("reply_to", replyTo);
        request.put("subject", email.subject());
        request.put("text", email.body());
        request.put("headers", headers);
        restClient.post()
                .uri("/emails")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private static void putIfPresent(Map<String, String> headers, String name, String value) {
        if (value != null) {
            headers.put(name, value);
        }
    }
}
