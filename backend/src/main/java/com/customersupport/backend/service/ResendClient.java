package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.ResendReceivedEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fetches received emails from Resend's API. Resend's email.received webhook only carries metadata; the body and
 * headers have to be retrieved separately with the API key.
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
}
