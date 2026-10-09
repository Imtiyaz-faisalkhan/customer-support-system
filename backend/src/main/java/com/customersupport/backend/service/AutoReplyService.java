package com.customersupport.backend.service;

import com.customersupport.backend.dto.KnowledgeBaseAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The AI's first reply to a new ticket. Runs in the background after the ticket is created, because the webhook that
 * created it has only ~15s before Resend retries, and categorization already uses part of that. If the knowledge
 * base answers the email, the AI reply is sent (AI_RESPONDED); otherwise the ticket goes to the admin (PENDING_HUMAN).
 * With AI off, the ticket stays OPEN. A restart while a reply is in flight leaves that ticket OPEN too.
 */
@Service
public class AutoReplyService {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyService.class);

    private final TicketService ticketService;
    private final KnowledgeBaseResponder responder;
    private final Executor executor;

    @Autowired
    public AutoReplyService(TicketService ticketService, KnowledgeBaseResponder responder) {
        this(ticketService, responder, Executors.newVirtualThreadPerTaskExecutor());
    }

    AutoReplyService(TicketService ticketService, KnowledgeBaseResponder responder, Executor executor) {
        this.ticketService = ticketService;
        this.responder = responder;
        this.executor = executor;
    }

    /** Called once the new ticket is committed; returns immediately. */
    public void replyAsync(Long ticketId, String subject, String body) {
        executor.execute(() -> reply(ticketId, subject, body));
    }

    private void reply(Long ticketId, String subject, String body) {
        try {
            Optional<KnowledgeBaseAnswer> answer = responder.answer(subject, body);
            if (answer.isEmpty()) {
                return;
            }
            if (answer.get().hasReply()) {
                try {
                    if (ticketService.sendAiReply(ticketId, answer.get().reply())) {
                        log.info("AI replied to ticket {}", ticketId);
                        return;
                    }
                    // Not sent: the admin answered first (escalating is then a no-op too), or the customer wrote
                    // again while the AI was drafting, which its answer doesn't cover.
                } catch (RuntimeException ex) {
                    // Rolled back, so the customer got nothing: the admin has to answer instead.
                    log.warn("AI reply to ticket {} not sent: {}", ticketId, ex.toString());
                }
            }
            if (ticketService.escalateToHuman(ticketId)) {
                log.info("Ticket {} escalated to the admin: no AI reply sent", ticketId);
            }
        } catch (RuntimeException ex) {
            // Nobody else would see this: it runs on its own thread after the webhook has returned.
            log.error("AI auto-reply for ticket {} failed", ticketId, ex);
        }
    }
}
