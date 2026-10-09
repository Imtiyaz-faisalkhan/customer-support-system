package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.MessageDto;
import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.dto.PageResponse;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.dto.TicketOpenedEvent;
import com.customersupport.backend.dto.TicketSummary;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.entity.TicketMessage;
import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.exception.InvalidStatusTransitionException;
import com.customersupport.backend.exception.TicketNotFoundException;
import com.customersupport.backend.exception.UndeliverableRecipientException;
import com.customersupport.backend.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TicketService {

    /** OPEN and AI_RESPONDED are set by the system (ingestion/AI), never by the admin directly. */
    private static final Set<TicketStatus> ADMIN_SETTABLE_STATUSES =
            EnumSet.of(TicketStatus.PENDING_HUMAN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);

    /** Reserved for documentation and testing (RFC 2606/6761): mail to them can never be delivered. */
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");
    private static final List<String> RESERVED_TLDS = List.of(".test", ".example", ".invalid", ".localhost");

    private final TicketRepository ticketRepository;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final ApplicationEventPublisher events;

    public TicketService(TicketRepository ticketRepository, EmailSender emailSender, EmailProperties emailProperties,
                         ApplicationEventPublisher events) {
        this.ticketRepository = ticketRepository;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.events = events;
    }

    /**
     * Creates a new OPEN ticket from an inbound customer email (see InboundEmailService). Once committed, it's
     * categorized in the background (TicketCategorizationListener).
     *
     * @param externalMessageId the email's Message-ID, or null
     */
    public TicketDetail openTicket(String customerEmail, String customerName, String subject, String body,
                                   String externalMessageId) {
        Ticket ticket = new Ticket(customerEmail, customerName, subject);
        ticket.addMessage(MessageAuthor.CUSTOMER, body, Instant.now(), externalMessageId);
        TicketDetail created = TicketDetail.from(ticketRepository.save(ticket));
        events.publishEvent(new TicketOpenedEvent(created.id()));
        return created;
    }

    /** The AI's category; ignored if the ticket already has one (the admin may have set it in the meantime). */
    public void applyAiCategory(Long ticketId, TicketCategory category) {
        Ticket ticket = findTicket(ticketId);
        if (ticket.getCategory() == null) {
            ticket.setCategory(category);
        }
    }

    /** The admin's choice, e.g. to correct the AI. */
    public TicketSummary updateCategory(Long id, TicketCategory category) {
        Ticket ticket = findTicket(id);
        ticket.setCategory(category);
        return TicketSummary.from(ticket);
    }

    /**
     * Appends a customer's emailed reply to their ticket. A reply to a RESOLVED or AI_RESPONDED ticket means the
     * customer isn't satisfied, so it goes back to PENDING_HUMAN; other statuses are already with the admin.
     */
    public MessageDto appendCustomerReply(Long ticketId, String body, String externalMessageId) {
        Ticket ticket = findTicket(ticketId);
        Instant now = Instant.now();
        TicketMessage message = ticket.addMessage(MessageAuthor.CUSTOMER, body, now, externalMessageId);
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.AI_RESPONDED) {
            ticket.changeStatus(TicketStatus.PENDING_HUMAN, now);
        }
        ticketRepository.flush();
        return MessageDto.from(message);
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
        if (emailProperties.rejectReservedRecipients() && isReservedAddress(ticket.getCustomerEmail())) {
            // Such a reply would bounce, and bounces hurt the sending domain's reputation with real email.
            throw new UndeliverableRecipientException(ticket.getCustomerEmail()
                    + " is a test address that can't receive email, so the reply was not sent.");
        }
        Instant now = Instant.now();
        // Threading: In-Reply-To = the customer's latest email, References = every earlier email in the thread.
        String inReplyTo = latestCustomerMessageId(ticket);
        String references = threadReferences(ticket);
        String messageId = "<" + UUID.randomUUID() + "@" + emailProperties.domain() + ">";
        TicketMessage message = ticket.addMessage(MessageAuthor.ADMIN, body, now, messageId);
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            ticket.changeStatus(TicketStatus.IN_PROGRESS, now);
        }
        // Flush (not save, whose cascaded merge would persist a copy) so the returned message gets its id.
        ticketRepository.flush();
        emailSender.send(new OutboundEmail(ticket.getCustomerEmail(), replySubject(ticket), body,
                messageId, inReplyTo, references));
        return MessageDto.from(message);
    }

    private static String latestCustomerMessageId(Ticket ticket) {
        return ticket.getMessages().reversed().stream()
                .filter(m -> m.getAuthor() == MessageAuthor.CUSTOMER && m.getExternalMessageId() != null)
                .map(TicketMessage::getExternalMessageId)
                .findFirst()
                .orElse(null);
    }

    private static String threadReferences(Ticket ticket) {
        String references = ticket.getMessages().stream()
                .map(TicketMessage::getExternalMessageId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return references.isEmpty() ? null : references;
    }

    private Ticket findTicket(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    private static boolean isReservedAddress(String address) {
        String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase();
        return RESERVED_DOMAINS.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d))
                || RESERVED_TLDS.stream().anyMatch(domain::endsWith);
    }

    /**
     * "Re: <subject> [#id-token]"; the tag lets InboundEmailService match a reply even without threading headers
     * (Resend may replace our Message-ID).
     */
    private static String replySubject(Ticket ticket) {
        String subject = ticket.getSubject();
        String reply = subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
        String tag = ticket.replyTag();
        return reply.contains(tag) ? reply : reply + " " + tag;
    }
}
