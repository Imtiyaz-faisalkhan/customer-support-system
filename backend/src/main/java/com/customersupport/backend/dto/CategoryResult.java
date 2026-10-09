package com.customersupport.backend.dto;

import com.customersupport.backend.enums.TicketCategory;

/** The structured answer AiTicketCategorizer asks the model for. */
public record CategoryResult(TicketCategory category) {
}
