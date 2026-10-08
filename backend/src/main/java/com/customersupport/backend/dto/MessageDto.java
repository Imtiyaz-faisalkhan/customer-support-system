package com.customersupport.backend.dto;

import com.customersupport.backend.entity.TicketMessage;
import com.customersupport.backend.enums.MessageAuthor;
import java.time.Instant;

public record MessageDto(Long id, MessageAuthor author, String body, Instant createdAt) {

    public static MessageDto from(TicketMessage message) {
        return new MessageDto(message.getId(), message.getAuthor(), message.getBody(), message.getCreatedAt());
    }
}
