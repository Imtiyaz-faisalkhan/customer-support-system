package com.customersupport.backend.dto;

import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import java.time.Instant;
import java.util.List;

public record TicketDetail(Long id,
                           String subject,
                           String customerEmail,
                           String customerName,
                           TicketStatus status,
                           TicketCategory category,
                           Instant createdAt,
                           Instant updatedAt,
                           Instant firstResponseAt,
                           Instant resolvedAt,
                           List<MessageDto> messages) {

    public static TicketDetail from(Ticket ticket) {
        return new TicketDetail(ticket.getId(), ticket.getSubject(), ticket.getCustomerEmail(),
                ticket.getCustomerName(), ticket.getStatus(), ticket.getCategory(),
                ApiTimestamps.toInstant(ticket.getCreatedAt()), ApiTimestamps.toInstant(ticket.getUpdatedAt()),
                ticket.getFirstResponseAt(), ticket.getResolvedAt(), ticket.getMessages().stream().map(MessageDto::from).toList());
    }
}
