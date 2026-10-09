package com.customersupport.backend.service;

import com.customersupport.backend.enums.TicketCategory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Used when AI is off ({@code spring.ai.model.chat} isn't {@code google-genai}, e.g. tests): tickets stay Uncategorized. */
@Component
@ConditionalOnExpression("'${spring.ai.model.chat:none}' != 'google-genai'")
public class NoOpTicketCategorizer implements TicketCategorizer {

    @Override
    public Optional<TicketCategory> categorize(String subject, String body) {
        return Optional.empty();
    }
}
