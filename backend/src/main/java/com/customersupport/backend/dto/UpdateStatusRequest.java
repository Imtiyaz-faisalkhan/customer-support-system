package com.customersupport.backend.dto;

import com.customersupport.backend.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull TicketStatus status) {
}
