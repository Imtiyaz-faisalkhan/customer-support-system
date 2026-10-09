package com.customersupport.backend.dto;

/**
 * The structured answer AiKnowledgeBaseResponder asks the model for: whether the knowledge base answers the email,
 * and if so the reply to send.
 */
public record KnowledgeBaseAnswer(boolean answerable, String reply) {

    public static KnowledgeBaseAnswer notAnswerable() {
        return new KnowledgeBaseAnswer(false, null);
    }

    /** Answerable and with an actual reply; the model saying "answerable" with an empty reply doesn't count. */
    public boolean hasReply() {
        return answerable && reply != null && !reply.isBlank();
    }
}
