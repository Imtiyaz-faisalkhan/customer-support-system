package com.customersupport.backend.dto;

import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;

import java.time.Instant;
import java.time.LocalDateTime;

/** The per-ticket facts the dashboard metrics are computed from (see TicketRepository.findMetricRowsCreatedSince). */
public record TicketMetricRow(LocalDateTime createdAt,
                              Instant firstResponseAt,
                              TicketStatus status,
                              TicketCategory category,
                              Boolean hasAdminReply) {
}
