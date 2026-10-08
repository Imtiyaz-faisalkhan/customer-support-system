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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ticket_messages")
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

    /** Provider message id (e.g. RFC 5322 Message-ID), reserved for threading inbound/outbound email. */
    private String externalMessageId;

    TicketMessage(Ticket ticket, MessageAuthor author, String body, Instant createdAt) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }
}
