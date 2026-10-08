package com.customersupport.backend.dto;

import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;

import java.time.Instant;

public record TicketSummary(Long id,
                            String subject,
                            String customerEmail,
                            String customerName,
                            TicketStatus status,
                            TicketCategory category,
                            Instant createdAt,
                            Instant updatedAt) {

    public static TicketSummary from(Ticket ticket) {
        return new TicketSummary(ticket.getId(), ticket.getSubject(), ticket.getCustomerEmail(),
                ticket.getCustomerName(), ticket.getStatus(), ticket.getCategory(),
                ApiTimestamps.toInstant(ticket.getCreatedAt()), ApiTimestamps.toInstant(ticket.getUpdatedAt()));
    }
}
