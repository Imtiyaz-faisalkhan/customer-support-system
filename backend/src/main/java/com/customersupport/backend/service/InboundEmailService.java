package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.entity.TicketMessage;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.repository.TicketMessageRepository;
import com.customersupport.backend.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides what an inbound customer email becomes: a new ticket, a reply on an existing ticket, or nothing
 * (duplicate delivery, auto-reply, our own mail). A new ticket then gets the AI's knowledge-base reply or is escalated
 * (AutoReplyService); replies on existing tickets never do. All ticket writes go through TicketService, each in its own
 * transaction. Deliberately not @Transactional itself: a new ticket is categorized by AI first, and that call
 * (seconds) must not hold a database connection.
 */
@Service
public class InboundEmailService {

    public enum Outcome { CREATED, APPENDED, DUPLICATE, IGNORED }

    public record Result(Outcome outcome, Long ticketId) {
    }

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);
    private static final Pattern TICKET_TAG = Pattern.compile("\\[#(\\d+)-([A-Z0-9]+)]");

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final EmailProperties emailProperties;
    private final TicketCategorizer categorizer;
    private final AutoReplyService autoReplyService;

    public InboundEmailService(TicketService ticketService, TicketRepository ticketRepository,
                               TicketMessageRepository messageRepository, EmailProperties emailProperties,
                               TicketCategorizer categorizer, AutoReplyService autoReplyService) {
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.emailProperties = emailProperties;
        this.categorizer = categorizer;
        this.autoReplyService = autoReplyService;
    }

    public Result receive(InboundEmail email) {
        if (email.automated() || email.fromAddress().equalsIgnoreCase(emailProperties.supportAddress())) {
            log.info("Ignoring automated or self-sent inbound email from={}", email.fromAddress());
            return new Result(Outcome.IGNORED, null);
        }
        // Resend retries webhooks it thinks failed; the same Message-ID must not be stored twice.
        if (email.messageId() != null && messageRepository.existsByExternalMessageId(email.messageId())) {
            return new Result(Outcome.DUPLICATE, null);
        }

        Optional<Ticket> existing = findTicketRepliedTo(email)
                // Only the ticket's own customer may add to it; anyone else starts a new ticket. The From address
                // can be forged, so this is only as strong as the thread match: a stored Message-ID or the random
                // token in the subject tag, both of which only appear in emails exchanged with that customer.
                .filter(ticket -> ticket.getCustomerEmail().equalsIgnoreCase(email.fromAddress()));
        if (existing.isPresent()) {
            Long ticketId = existing.get().getId();
            ticketService.appendCustomerReply(ticketId, email.body(), email.messageId());
            return new Result(Outcome.APPENDED, ticketId);
        }
        // Categorized before it's created, so the ticket never shows up as Uncategorized unless the AI is off or fails.
        TicketCategory category = categorizer.categorize(email.subject(), email.body()).orElse(null);
        Long ticketId = ticketService.openTicket(email.fromAddress(), email.fromName(), email.subject(),
                email.body(), email.messageId(), category).id();
        // In the background: the knowledge-base answer can take longer than Resend waits for this webhook.
        autoReplyService.replyAsync(ticketId, email.subject(), email.body());
        return new Result(Outcome.CREATED, ticketId);
    }

    /** Threading headers first (In-Reply-To/References -> a message we know), then the "[#123-TOKEN]" subject tag. */
    private Optional<Ticket> findTicketRepliedTo(InboundEmail email) {
        if (!email.threadIds().isEmpty()) {
            Optional<Ticket> byHeaders = messageRepository.findByExternalMessageIdIn(email.threadIds()).stream()
                    .map(TicketMessage::getTicket)
                    .findFirst();
            if (byHeaders.isPresent()) {
                return byHeaders;
            }
        }
        Matcher tag = TICKET_TAG.matcher(email.subject());
        if (!tag.find()) {
            return Optional.empty();
        }
        return ticketRepository.findById(Long.parseLong(tag.group(1)))
                .filter(ticket -> tag.group(2).equals(ticket.getReplyToken()));
    }
}
