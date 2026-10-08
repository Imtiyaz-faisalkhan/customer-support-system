package com.customersupport.backend.enums;

/** Ticket lifecycle, as defined in PROJECT_PLAN.md. */
public enum TicketStatus {
    OPEN,
    AI_RESPONDED,
    PENDING_HUMAN,
    IN_PROGRESS,
    RESOLVED
}
