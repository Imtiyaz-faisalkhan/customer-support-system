package com.customersupport.backend.entity;

import com.customersupport.backend.enums.MessageAuthor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
// Unique: an email Message-ID identifies exactly one message (dedupes webhook retries). NULLs allowed.
@Table(name = "ticket_messages",
        indexes = @Index(name = "ux_ticket_messages_external_message_id", columnList = "externalMessageId", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageAuthor author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    /** RFC 5322 Message-ID incl. angle brackets (e.g. "<abc@mail.gmail.com>") of the email this message was. */
    private String externalMessageId;

    TicketMessage(Ticket ticket, MessageAuthor author, String body, Instant createdAt, String externalMessageId) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
        this.externalMessageId = externalMessageId;
    }
}
