package com.customersupport.backend.dto;

/** Published by TicketService.openTicket; handled after the ticket is committed (TicketCategorizationListener). */
public record TicketOpenedEvent(Long ticketId) {
}
