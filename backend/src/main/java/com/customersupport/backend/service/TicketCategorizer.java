package com.customersupport.backend.service;

import com.customersupport.backend.enums.TicketCategory;

import java.util.Optional;

/** Picks a ticket's category from its first email. Empty when it can't (AI off, call failed, unusable answer). */
public interface TicketCategorizer {

    Optional<TicketCategory> categorize(String subject, String body);
}
