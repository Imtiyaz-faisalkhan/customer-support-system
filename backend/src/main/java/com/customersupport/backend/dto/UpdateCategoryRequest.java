package com.customersupport.backend.dto;

import com.customersupport.backend.enums.TicketCategory;
import jakarta.validation.constraints.NotNull;

public record UpdateCategoryRequest(@NotNull TicketCategory category) {
}
