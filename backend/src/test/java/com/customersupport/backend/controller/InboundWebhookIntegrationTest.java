package com.customersupport.backend.controller;

import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.repository.TicketRepository;
import com.customersupport.backend.service.EmailSender;
import com.customersupport.backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboundWebhookIntegrationTest {

    private static final String WEBHOOK = "/api/webhooks/sendgrid/inbound";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketService ticketService;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    /** The multipart fields SendGrid Inbound Parse posts (default, non-raw mode). */
    private static MockMultipartHttpServletRequestBuilder sendGridPost(String messageId, String inReplyTo,
                                                                      String subject, String text) {
        String headers = "From: Jane Doe <jane@example.com>\nMessage-ID: " + messageId + "\n"
                + (inReplyTo == null ? "" : "In-Reply-To: " + inReplyTo + "\n");
        return multipart(WEBHOOK)
                .param("from", "Jane Doe <jane@example.com>")
                .param("to", "support@test.example")
                .param("subject", subject)
                .param("text", text)
                .param("headers", headers);
    }

    @Test
    void rejectsMissingOrWrongCredentials() throws Exception {
        mockMvc.perform(sendGridPost("<a@x>", null, "Hi", "Hi"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(sendGridPost("<a@x>", null, "Hi", "Hi").with(httpBasic("sendgrid", "wrong")))
                .andExpect(status().isUnauthorized());
        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void newEmail_thenThreadedReply_thenRedelivery() throws Exception {
        String ticketId = mockMvc.perform(sendGridPost("<first@mail.example.com>", null, "Invoice", "Need my invoice")
                        .with(httpBasic("sendgrid", "test-webhook-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"ticketId\":(\\d+).*", "$1");
        Long id = Long.valueOf(ticketId);

        // Admin replies (stored with our Message-ID); the customer answers that email.
        ticketService.updateStatus(id, TicketStatus.RESOLVED);
        ticketService.reply(id, "Here it is");
        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().inReplyTo()).isEqualTo("<first@mail.example.com>"); // threads onto her email
        String ourMessageId = sent.getValue().messageId();

        mockMvc.perform(sendGridPost("<second@mail.example.com>", ourMessageId, "Re: Invoice", "Wrong month!")
                        .with(httpBasic("sendgrid", "test-webhook-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPENDED"))
                .andExpect(jsonPath("$.ticketId").value(id));

        // SendGrid redelivers the same email: nothing new is stored.
        mockMvc.perform(sendGridPost("<second@mail.example.com>", ourMessageId, "Re: Invoice", "Wrong month!")
                        .with(httpBasic("sendgrid", "test-webhook-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        var detail = ticketService.get(id);
        assertThat(ticketRepository.count()).isEqualTo(1);
        assertThat(detail.messages()).extracting(m -> m.body())
                .containsExactly("Need my invoice", "Here it is", "Wrong month!");
        // The customer answered a resolved ticket: it's back with the admin.
        assertThat(detail.status()).isEqualTo(TicketStatus.PENDING_HUMAN);
    }

    @Test
    void unparseableSender_isAcknowledgedButDropped() throws Exception {
        mockMvc.perform(multipart(WEBHOOK).param("from", "garbage").param("text", "x")
                        .with(httpBasic("sendgrid", "test-webhook-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        assertThat(ticketRepository.count()).isZero();
    }
}
