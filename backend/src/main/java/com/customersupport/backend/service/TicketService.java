package com.customersupport.backend.service;

import com.customersupport.backend.dto.MessageDto;
import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.dto.PageResponse;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.dto.TicketSummary;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.entity.TicketMessage;
import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.exception.InvalidStatusTransitionException;
import com.customersupport.backend.exception.TicketNotFoundException;
import com.customersupport.backend.repository.TicketRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@Transactional
public class TicketService {

    /** OPEN and AI_RESPONDED are set by the system (ingestion/AI), never by the admin directly. */
    private static final Set<TicketStatus> ADMIN_SETTABLE_STATUSES =
            EnumSet.of(TicketStatus.PENDING_HUMAN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);

    private final TicketRepository ticketRepository;
    private final EmailSender emailSender;

    public TicketService(TicketRepository ticketRepository, EmailSender emailSender) {
        this.ticketRepository = ticketRepository;
        this.emailSender = emailSender;
    }

    /** Creates a new OPEN ticket from an inbound customer email. Entry point for the future webhook. */
    public TicketDetail openTicket(String customerEmail, String customerName, String subject, String body) {
        Ticket ticket = new Ticket(customerEmail, customerName, subject);
        ticket.addMessage(MessageAuthor.CUSTOMER, body, Instant.now());
        return TicketDetail.from(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> list(TicketStatus status, TicketCategory category, Pageable pageable) {
        return PageResponse.from(ticketRepository.search(status, category, pageable), TicketSummary::from);
    }

    @Transactional(readOnly = true)
    public TicketDetail get(Long id) {
        return TicketDetail.from(findTicket(id));
    }

    public TicketSummary updateStatus(Long id, TicketStatus newStatus) {
        if (!ADMIN_SETTABLE_STATUSES.contains(newStatus)) {
            throw new InvalidStatusTransitionException("Status " + newStatus + " cannot be set manually");
        }
        Ticket ticket = findTicket(id);
        ticket.changeStatus(newStatus, Instant.now());
        return TicketSummary.from(ticket);
    }

    /**
     * Appends an admin reply and emails it to the customer. Sending happens inside the transaction, so a
     * send failure rolls back the reply rather than showing one in the thread the customer never got.
     */
    public MessageDto reply(Long id, String body) {
        Ticket ticket = findTicket(id);
        Instant now = Instant.now();
        TicketMessage message = ticket.addMessage(MessageAuthor.ADMIN, body, now);
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            ticket.changeStatus(TicketStatus.IN_PROGRESS, now);
        }
        // Flush (not save, whose cascaded merge would persist a copy) so the returned message gets its id.
        ticketRepository.flush();
        emailSender.send(new OutboundEmail(ticket.getCustomerEmail(), replySubject(ticket.getSubject()), body));
        return MessageDto.from(message);
    }

    private Ticket findTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    private static String replySubject(String subject) {
        return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
    }
}
