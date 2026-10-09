package com.customersupport.backend.config;

import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Local-dev only: fills an empty tickets table with realistic sample tickets so the API/UI have something
 * to show without real customer email. Enabled by app.demo-data.enabled=true (off in
 * scripts/run-backend-resend.sh, where replies are real emails); never seeds a table that already has tickets. Writes entities directly (not via TicketService) because it needs
 * system-only statuses, AI messages, and back-dated timestamps. Ticket createdAt/updatedAt are
 * Hibernate-generated (always "now"), so they're back-dated with a native UPDATE after saving.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoTicketSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTicketSeeder.class);

    private final TicketRepository ticketRepository;
    private final EntityManager entityManager;

    public DemoTicketSeeder(TicketRepository ticketRepository, EntityManager entityManager) {
        this.ticketRepository = ticketRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ticketRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<Ticket> tickets = List.of(
                // Brand new, not yet processed by AI.
                ticket(now.minus(Duration.ofMinutes(12)), "priya.sharma@example.com", "Priya Sharma",
                        "Can't log in after password reset", null,
                        "Hi, I reset my password this morning but the new one doesn't work. I've tried twice "
                                + "and now I'm locked out. Can you help?"),
                ticket(now.minus(Duration.ofHours(1)), "tom.becker@example.com", "Tom Becker",
                        "Question about your enterprise plan", null,
                        "Hello, we're a team of 40 and are evaluating your enterprise plan. Do you offer "
                                + "annual billing and SSO?"),

                // AI answered from the knowledge base; waiting to see if the customer is satisfied.
                withReply(ticket(now.minus(Duration.ofHours(5)), "lena.muller@example.com", "Lena Müller",
                                "How do I download my invoices?", TicketCategory.BILLING,
                                "Where can I find the invoices for the last 3 months? I need them for my accountant."),
                        MessageAuthor.AI, Duration.ofMinutes(2),
                        "Hi Lena, you can download all past invoices from Settings → Billing → Invoice history. "
                                + "Each invoice has a PDF download button on the right. Let us know if anything is missing!",
                        TicketStatus.AI_RESPONDED),
                withReply(ticket(now.minus(Duration.ofDays(1)), "carlos.diaz@example.com", "Carlos Díaz",
                                "Change account email address", TicketCategory.GENERAL,
                                "I'd like to change the email on my account to my work address. How do I do that?"),
                        MessageAuthor.AI, Duration.ofMinutes(1),
                        "Hi Carlos, go to Settings → Profile, click \"Change email\" and confirm the link we send "
                                + "to your new address. Your login will switch over once confirmed.",
                        TicketStatus.AI_RESPONDED),

                // AI couldn't resolve; escalated to the admin.
                status(ticket(now.minus(Duration.ofHours(3)), "aisha.khan@example.com", "Aisha Khan",
                                "Charged twice for October", TicketCategory.BILLING,
                                "My card was charged twice on Oct 1st for the same subscription. Please refund "
                                        + "the duplicate charge."),
                        TicketStatus.PENDING_HUMAN),
                withCustomerFollowUp(withReply(ticket(now.minus(Duration.ofDays(2)), "mark.o@example.com", "Mark O'Neil",
                                        "API returns 500 when uploading files", TicketCategory.TECHNICAL,
                                        "Since yesterday, POST /v2/files returns HTTP 500 for any file over 5MB. "
                                                + "Smaller files work fine."),
                                MessageAuthor.AI, Duration.ofMinutes(3),
                                "Hi Mark, uploads over 5MB need to use the multipart upload endpoint "
                                        + "(/v2/files/multipart). Could you try that and let us know?",
                                TicketStatus.AI_RESPONDED),
                        Duration.ofHours(4),
                        "We already use the multipart endpoint, it still fails with 500. This is blocking our release."),

                // Admin is working on it.
                withReply(ticket(now.minus(Duration.ofDays(3)), "sophie.martin@example.com", "Sophie Martin",
                                "Data export is missing last week's orders", TicketCategory.TECHNICAL,
                                "The CSV export from the dashboard doesn't include any orders from Sept 28 – Oct 4."),
                        MessageAuthor.ADMIN, Duration.ofHours(6),
                        "Hi Sophie, thanks for reporting this. I've reproduced it and our team is looking into the "
                                + "export job now. I'll update you as soon as it's fixed.",
                        TicketStatus.IN_PROGRESS),

                // Done.
                withReply(ticket(now.minus(Duration.ofDays(5)), "james.lee@example.com", "James Lee",
                                "Refund for cancelled subscription", TicketCategory.BILLING,
                                "I cancelled within the 14-day trial window but was still charged. Can I get a refund?"),
                        MessageAuthor.ADMIN, Duration.ofHours(9),
                        "Hi James, you're right, the charge shouldn't have happened. I've issued a full refund; "
                                + "it should appear on your statement within 5–7 business days.",
                        TicketStatus.RESOLVED),
                withReply(ticket(now.minus(Duration.ofDays(6)), "nina.petrova@example.com", "Nina Petrova",
                                "How do I enable two-factor authentication?", TicketCategory.GENERAL,
                                "Is there a way to turn on 2FA for my account?"),
                        MessageAuthor.AI, Duration.ofMinutes(1),
                        "Hi Nina, yes! Go to Settings → Security → Two-factor authentication and follow the steps "
                                + "to link an authenticator app.",
                        TicketStatus.RESOLVED));

        ticketRepository.saveAll(tickets);
        ticketRepository.flush();
        tickets.forEach(this::backdateTimestamps);
        log.info("Seeded {} demo tickets (app.demo-data.enabled=true)", tickets.size());
    }

    /** Sets created/updated to the ticket's first/last message times instead of the seeding moment. */
    private void backdateTimestamps(Ticket ticket) {
        entityManager.createNativeQuery("UPDATE tickets SET created_at = ?1, updated_at = ?2 WHERE id = ?3")
                .setParameter(1, toLocal(ticket.getMessages().getFirst().getCreatedAt()))
                .setParameter(2, toLocal(lastActivity(ticket)))
                .setParameter(3, ticket.getId())
                .executeUpdate();
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant lastActivity(Ticket ticket) {
        return ticket.getMessages().getLast().getCreatedAt();
    }

    private static Ticket ticket(Instant at, String email, String name, String subject,
                                 TicketCategory category, String body) {
        Ticket ticket = new Ticket(email, name, subject);
        ticket.setCategory(category);
        ticket.addMessage(MessageAuthor.CUSTOMER, body, at);
        return ticket;
    }

    private static Ticket withReply(Ticket ticket, MessageAuthor author, Duration after, String body,
                                    TicketStatus newStatus) {
        Instant at = lastActivity(ticket).plus(after);
        ticket.addMessage(author, body, at);
        ticket.changeStatus(newStatus, at);
        return ticket;
    }

    /** Customer replies that the answer didn't help, which escalates the ticket back to the admin. */
    private static Ticket withCustomerFollowUp(Ticket ticket, Duration after, String body) {
        Instant at = lastActivity(ticket).plus(after);
        ticket.addMessage(MessageAuthor.CUSTOMER, body, at);
        ticket.changeStatus(TicketStatus.PENDING_HUMAN, at);
        return ticket;
    }

    private static Ticket status(Ticket ticket, TicketStatus newStatus) {
        ticket.changeStatus(newStatus, lastActivity(ticket));
        return ticket;
    }
}
