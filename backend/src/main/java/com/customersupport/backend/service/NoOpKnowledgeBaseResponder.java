package com.customersupport.backend.service;

import com.customersupport.backend.dto.KnowledgeBaseAnswer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Used when AI is off ({@code spring.ai.model.chat} isn't {@code google-genai}, e.g. tests): new tickets stay OPEN. */
@Component
@ConditionalOnExpression("'${spring.ai.model.chat:none}' != 'google-genai'")
public class NoOpKnowledgeBaseResponder implements KnowledgeBaseResponder {

    @Override
    public Optional<KnowledgeBaseAnswer> answer(String subject, String body) {
        return Optional.empty();
    }
}
