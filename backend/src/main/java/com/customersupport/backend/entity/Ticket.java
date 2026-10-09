package com.customersupport.backend.entity;

import com.customersupport.backend.enums.MessageAuthor;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String customerEmail;

    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketCategory category;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last activity on the ticket (new message or status change); drives the default list ordering. */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** First non-customer reply (AI or admin); basis for the average-response-time metric. */
    private Instant firstResponseAt;

    private Instant resolvedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketMessage> messages = new ArrayList<>();

    public Ticket(String customerEmail, String customerName, String subject) {
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.subject = subject;
        this.status = TicketStatus.OPEN;
    }

    public TicketMessage addMessage(MessageAuthor author, String body, Instant now) {
        return addMessage(author, body, now, null);
    }

    /** @param externalMessageId the email Message-ID this message was sent/received as, or null */
    public TicketMessage addMessage(MessageAuthor author, String body, Instant now, String externalMessageId) {
        TicketMessage message = new TicketMessage(this, author, body, now, externalMessageId);
        messages.add(message);
        if (author != MessageAuthor.CUSTOMER && firstResponseAt == null) {
            firstResponseAt = now;
        }
        // @UpdateTimestamp only fires when a ticket column changes, and adding a child message doesn't change
        // one. Touching updatedAt marks the ticket dirty so Hibernate regenerates it on flush.
        updatedAt = LocalDateTime.now();
        return message;
    }

    public void changeStatus(TicketStatus newStatus, Instant now) {
        if (newStatus == TicketStatus.RESOLVED) {
            if (status != TicketStatus.RESOLVED) {
                resolvedAt = now;
            }
        } else {
            resolvedAt = null;
        }
        status = newStatus;
    }
}
