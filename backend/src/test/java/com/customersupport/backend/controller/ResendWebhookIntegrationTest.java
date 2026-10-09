package com.customersupport.backend.controller;

import com.customersupport.backend.dto.ResendReceivedEmail;
import com.customersupport.backend.repository.TicketRepository;
import com.customersupport.backend.service.ResendClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResendWebhookIntegrationTest {

    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw"; // application-test.properties

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @MockitoBean
    private ResendClient resendClient;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    /** Signs the body exactly like Resend/Svix does. */
    private static MockHttpServletRequestBuilder signedPost(String body) throws Exception {
        String id = "msg_test";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring(6)), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return post("/api/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("svix-id", id).header("svix-timestamp", timestamp).header("svix-signature", "v1," + signature);
    }

    private static String receivedEvent(String emailId) {
        return """
                {"type":"email.received","created_at":"2026-10-09T10:00:00Z",
                 "data":{"email_id":"%s","from":"sam@example.com","to":["help@support.example.com"],
                         "message_id":"<c1@example.com>","subject":"Package arrived damaged","attachments":[]}}
                """.formatted(emailId);
    }

    @Test
    void signedEmailReceivedEvent_createsTicketFromTheFetchedEmail() throws Exception {
        when(resendClient.getReceivedEmail("e-1")).thenReturn(new ResendReceivedEmail("e-1", "sam@example.com",
                "Package arrived damaged", "The screen is cracked.", null,
                Map.of("from", "Sam Carter <sam@example.com>"), "<c1@example.com>", List.of()));

        mockMvc.perform(signedPost(receivedEvent("e-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CREATED"));

        assertThat(ticketRepository.findAll()).singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.getCustomerEmail()).isEqualTo("sam@example.com");
                    assertThat(ticket.getCustomerName()).isEqualTo("Sam Carter");
                });
    }

    @Test
    void badOrMissingSignature_isRejectedWithoutCallingResend() throws Exception {
        mockMvc.perform(post("/api/webhooks/resend").contentType(MediaType.APPLICATION_JSON)
                        .content(receivedEvent("e-2")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(signedPost(receivedEvent("e-2")).header("svix-signature", "v1,AAAA"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(resendClient);
        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void otherEventTypes_areAcknowledgedAndIgnored() throws Exception {
        mockMvc.perform(signedPost("{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"x\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        verifyNoInteractions(resendClient);
    }

    @Test
    void resendApiDown_returns502SoResendRetries() throws Exception {
        when(resendClient.getReceivedEmail("e-3")).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(signedPost(receivedEvent("e-3")))
                .andExpect(status().isBadGateway());
        assertThat(ticketRepository.count()).isZero();
    }
}
