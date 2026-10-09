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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @MockitoBean
    private EmailSender emailSender;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        ticketRepository.deleteAll();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"changeme123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    void listWithoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_filtersByStatus() throws Exception {
        Long openId = ticketService.openTicket("a@example.com", "A", "Login issue", "Can't log in", null).id();
        Long resolvedId = ticketService.openTicket("b@example.com", "B", "Invoice", "Need an invoice", null).id();
        ticketService.updateStatus(resolvedId, TicketStatus.RESOLVED);

        mockMvc.perform(get("/api/tickets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/tickets").param("status", "OPEN").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(openId));
    }

    @Test
    void replyThenStatusChange_showsFullThreadAndSendsEmail() throws Exception {
        Long id = ticketService.openTicket("jane@example.com", "Jane", "Billing question", "Charged twice", null).id();

        mockMvc.perform(post("/api/tickets/{id}/replies", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Refunded the duplicate.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("ADMIN"))
                .andExpect(jsonPath("$.id").isNumber());

        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("jane@example.com");
        assertThat(sent.getValue().subject()).matches("Re: Billing question \\[#" + id + "-[A-Z0-9]{8}]");
        assertThat(sent.getValue().body()).isEqualTo("Refunded the duplicate.");

        mockMvc.perform(patch("/api/tickets/{id}/status", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/tickets/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                // Every timestamp is sent as a UTC instant, including the Hibernate-generated LocalDateTimes.
                .andExpect(jsonPath("$.createdAt").value(endsWith("Z")))
                .andExpect(jsonPath("$.updatedAt").value(endsWith("Z")))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].author").value("CUSTOMER"))
                .andExpect(jsonPath("$.messages[1].author").value("ADMIN"));
    }

    @Test
    void replyWithoutStatusChange_stillBumpsUpdatedAt() {
        Long id = ticketService.openTicket("jane@example.com", "Jane", "Hello", "Hi", null).id();
        ticketService.reply(id, "First reply");  // OPEN -> IN_PROGRESS
        Instant afterFirstReply = ticketService.get(id).updatedAt();

        ticketService.reply(id, "Second reply"); // stays IN_PROGRESS; only a message is added

        assertThat(ticketService.get(id).updatedAt()).isAfter(afterFirstReply);
    }

    @Test
    void invalidRequests_returnErrors() throws Exception {
        Long id = ticketService.openTicket("jane@example.com", "Jane", "Hello", "Hi", null).id();

        mockMvc.perform(get("/api/tickets/{id}", 999_999).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Ticket 999999 not found"));

        mockMvc.perform(post("/api/tickets/{id}/replies", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/tickets/{id}/replies", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("body size must be between 0 and 1000"));

        mockMvc.perform(patch("/api/tickets/{id}/status", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AI_RESPONDED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tickets").param("status", "NOT_A_STATUS").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanChangeTheCategory() throws Exception {
        Long id = ticketService.openTicket("jane@example.com", "Jane", "Invoice", "Need a copy", null).id();

        mockMvc.perform(patch("/api/tickets/{id}/category", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BILLING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLING"));

        mockMvc.perform(patch("/api/tickets/{id}/category", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SHIPPING\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/tickets/{id}/category", id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
