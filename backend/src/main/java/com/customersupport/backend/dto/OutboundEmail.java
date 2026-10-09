package com.customersupport.backend.dto;

/**
 * An email to send to a customer. The threading headers make it land in the customer's existing conversation:
 * messageId is ours (stored on the TicketMessage), inReplyTo/references point at earlier messages; both nullable.
 * autoReply = sent by the AI without a person (gets "Auto-Submitted: auto-replied", RFC 3834).
 */
public record OutboundEmail(String to,
                            String subject,
                            String body,
                            String messageId,
                            String inReplyTo,
                            String references,
                            boolean autoReply) {

    public OutboundEmail(String to, String subject, String body, String messageId, String inReplyTo,
                         String references) {
        this(to, subject, body, messageId, inReplyTo, references, false);
    }
}
