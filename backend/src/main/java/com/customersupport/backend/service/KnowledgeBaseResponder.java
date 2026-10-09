package com.customersupport.backend.service;

import com.customersupport.backend.dto.KnowledgeBaseAnswer;

import java.util.Optional;

/**
 * Drafts the reply to a new ticket's first email from the knowledge base. Empty when the AI is off (the ticket is
 * left alone); a failed or unusable answer is "not answerable", so the ticket goes to the admin.
 */
public interface KnowledgeBaseResponder {

    Optional<KnowledgeBaseAnswer> answer(String subject, String body);
}
