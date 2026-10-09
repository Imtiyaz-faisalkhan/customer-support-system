package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.entity.TicketMessage;
import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.repository.TicketMessageRepository;
import com.customersupport.backend.repository.TicketRepository;
import com.customersupport.backend.service.InboundEmailService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundEmailServiceTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMessageRepository messageRepository;

    private InboundEmailService service;

    @BeforeEach
    void setUp() {
        service = new InboundEmailService(ticketService, ticketRepository, messageRepository,
                new EmailProperties("support@test.example", "Support", null, false));
    }

    private static InboundEmail email(String from, String subject, String messageId, List<String> threadIds) {
        return new InboundEmail(from, "Jane", subject, "Hello", messageId, threadIds, false);
    }

    private static Ticket ticket(long id, String customerEmail) {
        Ticket ticket = new Ticket(customerEmail, "Jane", "Invoice");
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    @Test
    void newEmail_opensATicket() {
        TicketDetail created = mock(TicketDetail.class);
        when(created.id()).thenReturn(7L);
        when(ticketService.openTicket("jane@example.com", "Jane", "Invoice", "Hello", "<m1@x>")).thenReturn(created);

        InboundEmailService.Result result = service.receive(email("jane@example.com", "Invoice", "<m1@x>", List.of()));

        assertThat(result).isEqualTo(new InboundEmailService.Result(Outcome.CREATED, 7L));
    }

    @Test
    void replyMatchedByThreadingHeaders_isAppended() {
        Ticket ticket = ticket(3, "jane@example.com");
        TicketMessage ours = ticket.addMessage(MessageAuthor.ADMIN, "Sent", Instant.now(), "<ours@test.example>");
        when(messageRepository.findByExternalMessageIdIn(List.of("<ours@test.example>"))).thenReturn(List.of(ours));

        InboundEmailService.Result result = service.receive(
                email("Jane@Example.com", "Re: Invoice", "<m2@x>", List.of("<ours@test.example>")));

        assertThat(result).isEqualTo(new InboundEmailService.Result(Outcome.APPENDED, 3L));
        verify(ticketService).appendCustomerReply(3L, "Hello", "<m2@x>");
    }

    @Test
    void replyMatchedBySubjectTag_whenHeadersAreMissing() {
        Ticket ticket = ticket(3, "jane@example.com");
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(ticket));

        InboundEmailService.Result result = service.receive(
                email("jane@example.com", "Re: Invoice " + ticket.replyTag(), "<m3@x>", List.of()));

        assertThat(result.outcome()).isEqualTo(Outcome.APPENDED);
        verify(ticketService).appendCustomerReply(3L, "Hello", "<m3@x>");
    }

    @Test
    void subjectTagWithWrongOrMissingToken_opensANewTicket() {
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(ticket(3, "jane@example.com")));
        TicketDetail created = mock(TicketDetail.class);
        when(created.id()).thenReturn(8L);
        when(ticketService.openTicket(any(), any(), any(), any(), any())).thenReturn(created);

        // Ticket ids are sequential; without the token a forged sender can't target a ticket.
        assertThat(service.receive(email("jane@example.com", "Re: Invoice [#3-AAAAAAAA]", "<m4@x>", List.of())))
                .isEqualTo(new InboundEmailService.Result(Outcome.CREATED, 8L));
        assertThat(service.receive(email("jane@example.com", "Re: Invoice [#3]", "<m5@x>", List.of())))
                .isEqualTo(new InboundEmailService.Result(Outcome.CREATED, 8L));
    }

    @Test
    void someoneElseReplyingToATicket_opensANewTicketInstead() {
        Ticket ticket = ticket(3, "jane@example.com");
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(ticket));
        TicketDetail created = mock(TicketDetail.class);
        when(created.id()).thenReturn(8L);
        when(ticketService.openTicket(any(), any(), any(), any(), any())).thenReturn(created);

        InboundEmailService.Result result = service.receive(
                email("mallory@example.com", "Re: Invoice " + ticket.replyTag(), "<m4@x>", List.of()));

        assertThat(result).isEqualTo(new InboundEmailService.Result(Outcome.CREATED, 8L));
    }

    @Test
    void duplicateDelivery_isIgnored() {
        when(messageRepository.existsByExternalMessageId("<m1@x>")).thenReturn(true);

        InboundEmailService.Result result = service.receive(email("jane@example.com", "Invoice", "<m1@x>", List.of()));

        assertThat(result.outcome()).isEqualTo(Outcome.DUPLICATE);
        verifyNoInteractions(ticketService);
    }

    @Test
    void autoRepliesAndOurOwnMail_areIgnored() {
        InboundEmail outOfOffice = new InboundEmail("jane@example.com", null, "Away", "Away", "<m5@x>", List.of(), true);

        assertThat(service.receive(outOfOffice).outcome()).isEqualTo(Outcome.IGNORED);
        assertThat(service.receive(email("Support@Test.Example", "Loop", "<m6@x>", List.of())).outcome())
                .isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(ticketService);
    }
}
