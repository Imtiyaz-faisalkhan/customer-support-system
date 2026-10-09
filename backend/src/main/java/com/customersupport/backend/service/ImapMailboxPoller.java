package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.InboundEmail;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Receives customer email by polling the support mailbox over IMAP (e.g. IONOS Mail), for when no provider pushes
 * email to the webhook. Each poll imports every message in the inbox folder through the same InboundEmailParser /
 * InboundEmailService path as the webhook, then MOVES it to the processed folder. Read/unread flags are ignored on
 * purpose: someone opening an email in webmail first must not make the app skip it. What's left in the inbox is
 * exactly what hasn't been imported yet.
 * <p>
 * A message whose import fails for a transient reason (e.g. database down) stays in the inbox and is retried on
 * the next poll; a crash between importing and moving is harmless because the Message-ID dedupe skips it then.
 */
@Component
@ConditionalOnProperty(name = "app.email.inbound", havingValue = "imap")
public class ImapMailboxPoller {

    private static final Logger log = LoggerFactory.getLogger(ImapMailboxPoller.class);
    private static final String TIMEOUT_MS = "20000";

    private final InboundEmailParser parser;
    private final InboundEmailService inboundEmailService;
    private final EmailProperties.Imap imap;

    public ImapMailboxPoller(InboundEmailParser parser, InboundEmailService inboundEmailService,
                             EmailProperties emailProperties) {
        this.parser = parser;
        this.inboundEmailService = inboundEmailService;
        this.imap = emailProperties.imap();
    }

    @Scheduled(initialDelayString = "${app.email.imap.initial-delay:5s}",
            fixedDelayString = "${app.email.imap.poll-interval:30s}")
    public void scheduledPoll() {
        try {
            int imported = poll();
            if (imported > 0) {
                log.info("Imported {} email(s) from the support mailbox", imported);
            }
        } catch (MessagingException ex) {
            // Wrong password, server unreachable, ... - log and try again next time rather than kill the schedule.
            log.warn("Could not check the support mailbox {}@{}: {}", imap.username(), imap.host(), ex.getMessage());
        }
    }

    /** One pass over the inbox. Returns how many messages were handled and moved. */
    public int poll() throws MessagingException {
        String protocol = imap.ssl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail." + protocol + ".connectiontimeout", TIMEOUT_MS);
        props.put("mail." + protocol + ".timeout", TIMEOUT_MS);
        Session session = Session.getInstance(props);

        try (Store store = session.getStore(protocol)) {
            store.connect(imap.host(), imap.port(), imap.username(), imap.password());
            Folder processed = store.getFolder(imap.processedFolder());
            if (!processed.exists()) {
                processed.create(Folder.HOLDS_MESSAGES);
            }
            Folder inbox = store.getFolder(imap.folder());
            inbox.open(Folder.READ_WRITE);
            try {
                Message[] all = inbox.getMessages();
                Message[] batch = Arrays.copyOf(all, Math.min(all.length, imap.batchSize()));
                List<Message> handled = new ArrayList<>();
                for (Message message : batch) {
                    if (importMessage((MimeMessage) message)) {
                        handled.add(message);
                    }
                }
                if (!handled.isEmpty()) {
                    Message[] done = handled.toArray(Message[]::new);
                    inbox.copyMessages(done, processed);
                    for (Message message : done) {
                        message.setFlag(Flags.Flag.DELETED, true);
                    }
                }
                return handled.size();
            } finally {
                inbox.close(true); // expunge: completes the move of the copied messages
            }
        }
    }

    /** @return true if the message is done with (imported or deliberately skipped), false to retry next poll */
    private boolean importMessage(MimeMessage message) {
        InboundEmail email;
        try {
            email = parser.parse(message);
        } catch (IllegalArgumentException ex) {
            // Never going to parse; move it out of the way so it isn't retried forever. Still in the mailbox.
            log.warn("Skipping unreadable email in the support mailbox: {}", ex.getMessage());
            return true;
        }
        try {
            InboundEmailService.Result result = inboundEmailService.receive(email);
            log.info("Inbound email from={} outcome={} ticketId={}", email.fromAddress(), result.outcome(),
                    result.ticketId());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not import email from {} (will retry): {}", email.fromAddress(), ex.getMessage());
            return false;
        }
    }
}
