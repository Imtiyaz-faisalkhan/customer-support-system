package com.customersupport.backend.service;

import com.customersupport.backend.dto.MessageDto;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.dto.TicketOpenedEvent;
import com.customersupport.backend.enums.MessageAuthor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Categorizes each new ticket in the background once it's committed: the Resend webhook doesn't wait for the AI,
 * and an AI failure can never lose the ticket (it just stays Uncategorized for the admin to set).
 */
@Component
public class TicketCategorizationListener {

    private static final Logger log = LoggerFactory.getLogger(TicketCategorizationListener.class);

    private final TicketService ticketService;
    private final TicketCategorizer categorizer;

    public TicketCategorizationListener(TicketService ticketService, TicketCategorizer categorizer) {
        this.ticketService = ticketService;
        this.categorizer = categorizer;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketOpened(TicketOpenedEvent event) {
        try {
            TicketDetail ticket = ticketService.get(event.ticketId());
            String body = ticket.messages().stream()
                    .filter(message -> message.author() == MessageAuthor.CUSTOMER)
                    .map(MessageDto::body)
                    .findFirst()
                    .orElse("");
            categorizer.categorize(ticket.subject(), body)
                    .ifPresent(category -> ticketService.applyAiCategory(event.ticketId(), category));
        } catch (RuntimeException ex) {
            log.warn("Could not categorize ticket {}", event.ticketId(), ex);
        }
    }
}
