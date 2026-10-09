package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.MessageDto;
import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.exception.InvalidStatusTransitionException;
import com.customersupport.backend.exception.TicketNotFoundException;
import com.customersupport.backend.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EmailSender emailSender;

    private TicketService service;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepository, emailSender,
                new EmailProperties("support@test.example", "Support", null, null, null));
        ticket = new Ticket("jane@example.com", "Jane", "Billing question");
        ticket.addMessage(MessageAuthor.CUSTOMER, "Why was I charged twice?", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void reply_appendsAdminMessage_sendsEmail_andMovesToInProgress() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        MessageDto reply = service.reply(1L, "We've refunded the duplicate charge.");

        assertThat(reply.author()).isEqualTo(MessageAuthor.ADMIN);
        assertThat(ticket.getMessages()).hasSize(2);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getFirstResponseAt()).isNotNull();
        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("jane@example.com");
        assertThat(sent.getValue().body()).isEqualTo("We've refunded the duplicate charge.");
        // Our Message-ID is on our domain and stored on the message, so the customer's reply can find this ticket.
        assertThat(sent.getValue().messageId()).matches("<[0-9a-f-]+@test\\.example>");
        assertThat(ticket.getMessages().getLast().getExternalMessageId()).isEqualTo(sent.getValue().messageId());
    }

    @Test
    void reply_keepsFirstResponseAtFromTheFirstReply() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        service.reply(1L, "First");
        Instant firstResponseAt = ticket.getFirstResponseAt();
        service.reply(1L, "Second");

        assertThat(ticket.getFirstResponseAt()).isEqualTo(firstResponseAt);
    }

    @Test
    void reply_toResolvedTicket_staysResolved() {
        ticket.changeStatus(TicketStatus.RESOLVED, Instant.now());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        service.reply(1L, "Following up.");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void updateStatus_toSystemOnlyStatus_isRejected() {
        assertThatThrownBy(() -> service.updateStatus(1L, TicketStatus.OPEN))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> service.updateStatus(1L, TicketStatus.AI_RESPONDED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void updateStatus_resolvedSetsResolvedAt_andReopeningClearsIt() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        service.updateStatus(1L, TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();

        service.updateStatus(1L, TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void get_unknownTicket_throwsNotFound() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(TicketNotFoundException.class);
    }
}
