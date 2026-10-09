package com.customersupport.backend.dto;

import java.util.List;

/**
 * A customer email received through the SendGrid Inbound Parse webhook, already parsed and cleaned
 * (see InboundEmailParser).
 *
 * @param fromAddress     sender address, lower-cased
 * @param fromName        sender display name, or null
 * @param body            plain-text body with quoted history stripped (never blank)
 * @param messageId       this email's Message-ID incl. angle brackets, or null if the sender omitted it
 * @param threadIds       Message-IDs from In-Reply-To and References, most relevant first
 * @param automated       auto-reply / bulk mail (Auto-Submitted, Precedence) that must not become a ticket
 */
public record InboundEmail(String fromAddress,
                           String fromName,
                           String subject,
                           String body,
                           String messageId,
                           List<String> threadIds,
                           boolean automated) {
}
